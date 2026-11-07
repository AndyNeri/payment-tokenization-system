package com.farmatodo.challenge.notification.infrastructure.out.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.farmatodo.challenge.notification.application.NotificationProperties;
import com.farmatodo.challenge.notification.domain.Email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    @Test
    void sendsAPlainTextMessageFromTheConfiguredSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        new SmtpEmailSender(mailSender, new NotificationProperties("no-reply@shop.dev"))
                .send(new Email("ana@mail.com", "Asunto", "Cuerpo"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@shop.dev");
        assertThat(message.getTo()).containsExactly("ana@mail.com");
        assertThat(message.getSubject()).isEqualTo("Asunto");
        assertThat(message.getText()).isEqualTo("Cuerpo");
    }
}
