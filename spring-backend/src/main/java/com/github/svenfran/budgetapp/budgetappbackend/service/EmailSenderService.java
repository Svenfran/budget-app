package com.github.svenfran.budgetapp.budgetappbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {

    @Autowired
    private BrevoMailSenderService mailSender;

    public void sendEmail(String recipient, String body, String subject) throws Exception {
        mailSender.sendEmail(recipient, subject, body);
    }
}
