package com.cricketplex.service;

import com.cricketplex.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.sender-name:CricketPlex}")
    private String senderName;

    @Value("${app.brevo.api-url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    @Value("${app.brevo.api-key}")
    private String brevoApiKey;

    public void sendVerificationEmail(User user) {
        String verifyUrl = frontendUrl + "/verify-email?token=" + user.getEmailVerificationToken();
        sendEmail(
                user.getEmail(),
                "Verify your CricketPlex account",
                "Hi " + user.getName() + ",\n\n"
                        + "Welcome to CricketPlex. Please verify your email by clicking the link below:\n"
                        + verifyUrl + "\n\n"
                        + "This link will expire in 24 hours.\n\n"
                        + "If you did not create this account, you can ignore this email.\n"
        );
    }

    public void sendPasswordResetEmail(User user) {
        String resetUrl = frontendUrl + "/reset-password?token=" + user.getPasswordResetToken();
        sendEmail(
                user.getEmail(),
                "Reset your CricketPlex password",
                "Hi " + user.getName() + ",\n\n"
                        + "We received a request to reset your password. Click the link below:\n"
                        + resetUrl + "\n\n"
                        + "This link will expire in 30 minutes.\n\n"
                        + "If you did not request this, you can ignore this email.\n"
        );
    }

    private void sendEmail(String toEmail, String subject, String textContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException("Brevo API key is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", senderName);
        sender.put("email", fromEmail);

        Map<String, Object> to = new LinkedHashMap<>();
        to.put("email", toEmail);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("to", List.of(to));
        payload.put("subject", subject);
        payload.put("textContent", textContent);

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(brevoApiUrl, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Brevo API call failed with status " + response.getStatusCode().value());
            }
        } catch (RestClientException ex) {
            throw new IllegalStateException("Brevo API call failed: " + ex.getMessage(), ex);
        }
    }
}
