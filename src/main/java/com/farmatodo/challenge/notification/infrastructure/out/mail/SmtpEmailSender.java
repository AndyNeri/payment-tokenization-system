package com.farmatodo.challenge.notification.infrastructure.out.mail;

import com.farmatodo.challenge.notification.application.NotificationProperties;
import com.farmatodo.challenge.notification.domain.Email;
import com.farmatodo.challenge.notification.domain.EmailSender;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter: SMTP through Spring's {@link JavaMailSender}. The provider is pure configuration
 * (Mailpit locally, Brevo in GCP): host, port, credentials and STARTTLS come from environment variables.
 */
@Component
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(Email email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email.to());
        message.setSubject(email.subject());
        message.setText(email.body());
        mailSender.send(message);   // MailException (unchecked) propagates: the event will be retried
    }
}
