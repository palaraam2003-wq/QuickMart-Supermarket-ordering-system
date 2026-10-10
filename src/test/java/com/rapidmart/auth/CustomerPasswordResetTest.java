package com.rapidmart.auth;

import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.user.entity.User;
import com.rapidmart.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.ArgumentCaptor;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.mail.host=smtp.test.invalid")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerPasswordResetTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired PasswordResetService resets;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockBean JavaMailSender sender;
    private User customer() {
        return users.saveAndFlush(new User(UUID.randomUUID()+"@example.com", encoder.encode("OldPassword1!"),
                "Reset Customer", null, true, Set.of(Role.CUSTOMER)));
    }
    private String code(User user) {
        resets.request(user.getEmail());
        var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue().getText().replaceAll("(?s).*code is ([0-9]{6}).*", "$1");
    }
    private PasswordResetController.ResetRequest request(User user, String code) {
        return new PasswordResetController.ResetRequest(user.getEmail(),code,"NewPassword1!","NewPassword1!");
    }
    @Test void changesPasswordAndConsumesCode() {
        User user=customer(); String code=code(user);
        String stored=jdbc.queryForObject("SELECT code_hash FROM customer_password_resets WHERE user_id=?",String.class,user.getId());
        assertNotEquals(code,stored);
        resets.confirm(request(user,code));
        assertTrue(encoder.matches("NewPassword1!",users.findById(user.getId()).orElseThrow().getPasswordHash()));
        assertThrows(BusinessException.class,()->resets.confirm(request(user,code)));
    }
    @Test void expiresAndLimitsAttempts() {
        User user=customer(); String code=code(user);
        String wrong=code.equals("000000")?"000001":"000000";
        for(int i=0;i<5;i++) assertThrows(BusinessException.class,()->resets.confirm(request(user,wrong)));
        assertThrows(BusinessException.class,()->resets.confirm(request(user,code)));
        jdbc.update("UPDATE customer_password_resets SET attempts=0,expires_at=DATEADD(minute,-1,SYSDATETIMEOFFSET()) WHERE user_id=?",user.getId());
        assertThrows(BusinessException.class,()->resets.confirm(request(user,code)));
    }
    @Test void cooldownDoesNotResendAndUnknownAccountIsGeneric() {
        User user=customer(); code(user); resets.request(user.getEmail());
        resets.request("unknown@example.com");
        verify(sender,times(1)).send(any(SimpleMailMessage.class));
    }
    @Test void emailRulesSeparatePortals() {
        assertEquals("person@example.com",LoginEmailRules.validate(" Person@Example.com ",false));
        assertEquals("cashier@rapidmart.local",LoginEmailRules.validate("cashier@rapidmart.local",true));
        assertThrows(BusinessException.class,()->LoginEmailRules.validate("person@rapidmart.local",false));
        assertThrows(BusinessException.class,()->LoginEmailRules.validate("person@example.com",true));
        assertThrows(BusinessException.class,()->LoginEmailRules.validate("person@example.com.evil",false));
    }
    @Test void loginChecksPortalAndRole() throws Exception {
        User customer=customer();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of(
                    "email",customer.getEmail(),"password","OldPassword1!","portal","staff"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        // Persisted roles are loaded as mutable Hibernate collections in real requests.
        customer.setRoles(new java.util.HashSet<>(Set.of(Role.CUSTOMER)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of(
                    "email",customer.getEmail(),"password","OldPassword1!","portal","customer"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        User staff=users.saveAndFlush(new User(UUID.randomUUID()+"@rapidmart.local",encoder.encode("StaffPass1!"),
                "Staff",null,true,new java.util.HashSet<>(Set.of(Role.CASHIER))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content(mapper.writeValueAsString(java.util.Map.of(
                    "email",staff.getEmail(),"password","StaffPass1!","portal","staff"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
