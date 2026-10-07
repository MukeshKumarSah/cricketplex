package com.cricketplex.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationClient {

    private final RestTemplate restTemplate;

    @Value("${app.social-service.url:http://localhost:8083}")
    private String socialServiceUrl;

    @Value("${app.internal.api-key:cricketplex-internal}")
    private String internalApiKey;

    public void create(UUID userId, String type, String title, String body, String link) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Key", internalApiKey);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId.toString());
            payload.put("type", type);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("link", link);

            restTemplate.postForEntity(
                    socialServiceUrl + "/internal/notifications",
                    new HttpEntity<>(payload, headers),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to send notification to social-service: {}", e.getMessage());
        }
    }
}
