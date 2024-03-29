package com.github.svenfran.budgetapp.budgetappbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(String recipient, String body, String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(System.getenv("email_username"));
        message.setTo(recipient);
        message.setText(body);
        message.setSubject(subject);
        mailSender.send(message);
    }
}
