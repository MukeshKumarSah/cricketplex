package com.cricketplex.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferEventClient {

    private final RestTemplate restTemplate;

    @Value("${app.social-service.url:http://localhost:8083}")
    private String socialServiceUrl;

    @Value("${app.internal.api-key:cricketplex-internal}")
    private String internalApiKey;

    public void broadcast(Object payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Key", internalApiKey);
            restTemplate.postForEntity(
                    socialServiceUrl + "/internal/transfer-events",
                    new HttpEntity<>(payload, headers),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast transfer event: {}", e.getMessage());
        }
    }
}
