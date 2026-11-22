package com.rmbreeding.api.notifications;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

public class SmtpEmailSender implements EmailSender {

    private final JavaMailSenderImpl mailSender;
    private final String from;

    public SmtpEmailSender(NotificationProperties.Email properties) {
        this.mailSender = new JavaMailSenderImpl();
        this.mailSender.setHost(properties.getHost());
        this.mailSender.setPort(properties.getPort());
        this.mailSender.setDefaultEncoding("UTF-8");
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            this.mailSender.setUsername(properties.getUsername());
            this.mailSender.setPassword(properties.getPassword());
        }
        Properties javaMail = this.mailSender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", String.valueOf(properties.getUsername() != null
                && !properties.getUsername().isBlank()));
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(properties.isStarttls()));
        javaMail.put("mail.smtp.ssl.enable", String.valueOf(properties.isSsl()));
        javaMail.put("mail.smtp.connectiontimeout", "5000");
        javaMail.put("mail.smtp.timeout", "10000");
        javaMail.put("mail.smtp.writetimeout", "10000");
        this.from = properties.getFrom();
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
