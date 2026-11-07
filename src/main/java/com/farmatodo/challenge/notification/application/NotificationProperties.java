package com.farmatodo.challenge.notification.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Sender address of every email ({@code MAIL_FROM}). Must be a verified sender at the SMTP provider. */
@Validated
@ConfigurationProperties("app.notification")
public record NotificationProperties(@NotBlank @Email String from) {
}
