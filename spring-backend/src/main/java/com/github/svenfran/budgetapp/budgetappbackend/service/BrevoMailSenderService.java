package com.github.svenfran.budgetapp.budgetappbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.Configuration;
import sibApi.TransactionalEmailsApi;
import sibModel.SendSmtpEmail;
import sibModel.SendSmtpEmailSender;
import sibModel.SendSmtpEmailTo;

import java.util.Collections;

@Service
public class BrevoMailSenderService {

    private final TransactionalEmailsApi emailApi;
    private final String senderEmail;
    private final String senderName;

    public BrevoMailSenderService(
            @Value("${brevo.api.key}") String apiKey,
            @Value("${brevo.sender.email}") String senderEmail,
            @Value("${brevo.sender.name}") String senderName
    ) {
        ApiClient client = Configuration.getDefaultApiClient();
        client.setApiKey(apiKey);
        this.emailApi = new TransactionalEmailsApi(client);

        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    public void sendEmail(String to, String subject, String content) throws Exception {
        SendSmtpEmailSender sender = new SendSmtpEmailSender()
                .email(senderEmail)
                .name(senderName);

        SendSmtpEmailTo recipient = new SendSmtpEmailTo().email(to);

        SendSmtpEmail email = new SendSmtpEmail()
                .sender(sender)
                .to(Collections.singletonList(recipient))
                .subject(subject)
                .htmlContent(content);

        emailApi.sendTransacEmail(email);
    }
}
