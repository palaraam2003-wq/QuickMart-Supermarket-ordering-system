package com.rapidmart.notification.service;

import com.rapidmart.notification.entity.NotificationLog;
import com.rapidmart.notification.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository notificationLogRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${rapidmart.mail.from:no-reply@rapidmart.local}")
    private String mailFrom;

    public NotificationService(NotificationLogRepository notificationLogRepository,
                               ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.notificationLogRepository = notificationLogRepository;
        this.mailSenderProvider = mailSenderProvider;
    }

    private boolean mailConfigured() {
        return mailHost != null && !mailHost.isBlank();
    }

    /** No SMTP host configured -> always a truthful NOT_SENT, never a fake success. */
    @Transactional
    public String sendEmail(String recipientEmail, String subject, String message) {
        return sendEmailWithAttachment(recipientEmail, subject, message, null, null);
    }

    /**
     * Same truthful-fallback behaviour as sendEmail, with an optional PDF attachment for bill emails.
     * Never throws: a send failure is caught, logged as FAILED, and returned to the caller to surface
     * as a clear error message rather than a 500.
     */
    @Transactional
    public String sendEmailWithAttachment(String recipientEmail, String subject, String message,
                                          byte[] attachment, String attachmentFilename) {
        if (!mailConfigured()) {
            notificationLogRepository.save(new NotificationLog(recipientEmail, "EMAIL", subject, message, "NOT_SENT"));
            return "NOT_SENT";
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            notificationLogRepository.save(new NotificationLog(recipientEmail, "EMAIL", subject, message, "NOT_SENT"));
            return "NOT_SENT";
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, attachment != null);
            helper.setFrom(mailFrom);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(message);
            if (attachment != null && attachmentFilename != null) {
                helper.addAttachment(attachmentFilename, new org.springframework.core.io.ByteArrayResource(attachment));
            }
            mailSender.send(mimeMessage);
            notificationLogRepository.save(new NotificationLog(recipientEmail, "EMAIL", subject, message, "SENT"));
            return "SENT";
        } catch (Exception ex) {
            log.warn("Email delivery to {} failed: {}", recipientEmail, ex.getMessage());
            notificationLogRepository.save(new NotificationLog(recipientEmail, "EMAIL", subject,
                    message, "FAILED: " + ex.getMessage()));
            return "FAILED";
        }
    }

    @Transactional
    public String sendSms(String recipientPhone, String message) {
        // No external SMS gateway configured -> truthful NOT_SENT result
        NotificationLog log = new NotificationLog(recipientPhone, "SMS", null, message, "NOT_SENT");
        notificationLogRepository.save(log);
        return "NOT_SENT";
    }
}
