package com.rapidmart.auth;

import com.rapidmart.common.enums.Role;
import com.rapidmart.common.exception.BusinessException;
import com.rapidmart.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.*;
import java.util.Set;

@Service
public class PasswordResetService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mail;
    private final SecureRandom random = new SecureRandom();
    @Value("${rapidmart.mail.from:no-reply@rapidmart.local}") private String from;
    @Value("${spring.mail.host:}") private String mailHost;
    public PasswordResetService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder,
            ObjectProvider<JavaMailSender> mail) {
        this.jdbc=jdbc; this.users=users; this.encoder=encoder; this.mail=mail;
    }
    private void lock(Long id) {
        jdbc.queryForObject("SELECT id FROM app_users WITH (UPDLOCK, HOLDLOCK) WHERE id=?", Long.class, id);
    }
    @Transactional
    public void request(String address) {
        String email = LoginEmailRules.validate(address, false);
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || mailHost == null || mailHost.isBlank())
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_NOT_CONFIGURED", "Outgoing email is not configured. Please contact the store to enable password reset emails.");
        var user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isActive() || !user.getRoles().equals(Set.of(Role.CUSTOMER))) return;
        lock(user.getId());
        var times = jdbc.query("SELECT requested_at FROM customer_password_resets WHERE user_id=?",
                (rs,n) -> rs.getObject(1, OffsetDateTime.class), user.getId());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Keep the same generic response during cooldown to avoid account enumeration.
        if (!times.isEmpty() && times.getFirst().plusMinutes(1).isAfter(now)) return;
        String otp = String.format("%06d", random.nextInt(1_000_000));
        jdbc.update("DELETE FROM customer_password_resets WHERE user_id=?", user.getId());
        jdbc.update("INSERT INTO customer_password_resets (user_id,code_hash,requested_at,expires_at,attempts) VALUES (?,?,?,?,0)",
                user.getId(), encoder.encode(otp), now, now.plusMinutes(10));
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("RapidMart password reset code");
        message.setText("Your RapidMart password reset code is " + otp
                + ". It expires in 10 minutes. If you did not request this, ignore this email.");
        try { sender.send(message); }
        catch (org.springframework.mail.MailException ex) {
            // Do not log the exception body: it may contain the OTP message or SMTP credentials.
            org.slf4j.LoggerFactory.getLogger(PasswordResetService.class)
                    .warn("Password reset email failed ({})", ex.getClass().getSimpleName());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_UNAVAILABLE",
                    "The reset email could not be sent. Please try again later.");
        }
    }
    private record Code(String hash, OffsetDateTime expiry, int attempts) {}
    @Transactional(noRollbackFor=BusinessException.class)
    public void confirm(PasswordResetController.ResetRequest request) {
        String email = LoginEmailRules.validate(request.email(), false);
        if (!request.password().equals(request.confirmPassword()))
            throw new BusinessException("PASSWORD_MISMATCH", "Passwords must match.");
        var user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isActive() || !user.getRoles().equals(Set.of(Role.CUSTOMER))) throw invalid();
        lock(user.getId());
        var codes = jdbc.query("SELECT code_hash,expires_at,attempts FROM customer_password_resets WHERE user_id=?",
                (rs,n) -> new Code(rs.getString(1),rs.getObject(2,OffsetDateTime.class),rs.getInt(3)), user.getId());
        if (codes.isEmpty()) throw invalid();
        Code code = codes.getFirst();
        if (code.attempts() >= 5 || !code.expiry().isAfter(OffsetDateTime.now())) throw invalid();
        if (!encoder.matches(request.otp(), code.hash())) {
            jdbc.update("UPDATE customer_password_resets SET attempts=attempts+1 WHERE user_id=?", user.getId());
            throw invalid();
        }
        user.setPasswordHash(encoder.encode(request.password()));
        users.flush();
        jdbc.update("DELETE FROM customer_password_resets WHERE user_id=?", user.getId());
    }
    private BusinessException invalid() {
        return new BusinessException("INVALID_RESET_CODE", "Invalid or expired code. Request a new code if needed.");
    }
}
