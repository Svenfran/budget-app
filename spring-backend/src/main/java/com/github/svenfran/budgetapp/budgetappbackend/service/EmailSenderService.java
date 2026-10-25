package com.github.svenfran.budgetapp.budgetappbackend.service;

import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {

    private final BrevoMailSenderService mailSender;

    public EmailSenderService(BrevoMailSenderService mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String recipient, String body, String subject) throws Exception {
        mailSender.sendEmail(recipient, subject, body);
    }
}
