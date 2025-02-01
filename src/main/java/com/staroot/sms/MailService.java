package com.staroot.sms;

import lombok.RequiredArgsConstructor;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MailService {
    private final JavaMailSender mailSender;

    @Async
    public CompletableFuture<RecipientDTO> sendEmail(RecipientDTO recipient, String subject, String template) {
        try {
            String content = replaceTemplateVariables(template, recipient.getCustomData());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipient.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            recipient.setSuccess(true);
        } catch (MessagingException e) {
            recipient.setSuccess(false);
            recipient.setErrorMessage(e.getMessage());
        }
        return CompletableFuture.completedFuture(recipient);
    }

    private String replaceTemplateVariables(String template, Map<String, String> data) {
        String result = template;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}