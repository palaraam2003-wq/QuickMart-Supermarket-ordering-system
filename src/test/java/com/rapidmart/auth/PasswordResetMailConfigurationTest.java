package com.rapidmart.auth;

import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PasswordResetMailConfigurationTest {
    @Test void emptyHostDoesNotAttemptDeliveryEvenWhenSenderBeanExists() {
        var jdbc = mock(JdbcTemplate.class);
        var users = mock(UserRepository.class);
        var sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        var service = new PasswordResetService(jdbc, users, mock(PasswordEncoder.class), provider);
        ReflectionTestUtils.setField(service, "mailHost", "");
        var error = assertThrows(BusinessException.class, () -> service.request("customer@example.com"));
        assertEquals("EMAIL_NOT_CONFIGURED", error.getErrorCode());
        verifyNoInteractions(jdbc, users, sender);
    }
}
