package com.cricketplex.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PayPalService {

    @Value("${app.paypal.client-id:}")
    private String clientId;

    @Value("${app.paypal.client-secret:}")
    private String clientSecret;

    @Value("${app.paypal.mode:sandbox}")
    private String mode;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getClientId() {
        return clientId == null ? "" : clientId;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    private String baseUrl() {
        return "live".equals(mode)
                ? "https://api.paypal.com"
                : "https://api.sandbox.paypal.com";
    }

    @SuppressWarnings("unchecked")
    private String accessToken() {
        String url = baseUrl() + "/v1/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) throw new IllegalStateException("Empty PayPal token response");
        return (String) responseBody.get("access_token");
    }

    /**
     * Create a PayPal order. Returns the PayPal order ID.
     */
    @SuppressWarnings("unchecked")
    public String createOrder(String planCode, double amountUsd, String userId) {
        ensureConfigured();
        String token = accessToken();
        String url = baseUrl() + "/v2/checkout/orders";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> amount = Map.of("currency_code", "USD",
                "value", String.format("%.2f", amountUsd));
        Map<String, Object> unit = Map.of(
                "description", "CricketPlex " + planCode + " Supporter",
                "custom_id", userId + "|" + planCode,
                "amount", amount);

        Map<String, Object> requestBody = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(unit));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) throw new IllegalStateException("Empty PayPal create-order response");
            return (String) responseBody.get("id");
        } catch (HttpClientErrorException ex) {
            log.error("PayPal createOrder failed: {}", ex.getResponseBodyAsString());
            throw new IllegalArgumentException("Unable to create PayPal order");
        }
    }

    /**
     * Capture a PayPal order after buyer approval. Returns the capture ID.
     */
    @SuppressWarnings("unchecked")
    public String captureOrder(String orderId) {
        ensureConfigured();
        String token = accessToken();
        String url = baseUrl() + "/v2/checkout/orders/" + orderId + "/capture";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) throw new IllegalStateException("Empty PayPal capture response");

            String status = (String) responseBody.get("status");
            if (!"COMPLETED".equals(status)) {
                throw new IllegalArgumentException("Payment not completed, status: " + status);
            }

            // Extract the capture ID from purchase_units[0].payments.captures[0].id
            List<Map<String, Object>> purchaseUnits =
                    (List<Map<String, Object>>) responseBody.get("purchase_units");
            if (purchaseUnits != null && !purchaseUnits.isEmpty()) {
                Map<String, Object> payments =
                        (Map<String, Object>) purchaseUnits.get(0).get("payments");
                if (payments != null) {
                    List<Map<String, Object>> captures =
                            (List<Map<String, Object>>) payments.get("captures");
                    if (captures != null && !captures.isEmpty()) {
                        return (String) captures.get(0).get("id");
                    }
                }
            }
            return orderId; // fallback
        } catch (HttpClientErrorException ex) {
            log.error("PayPal captureOrder failed: {}", ex.getResponseBodyAsString());
            throw new IllegalArgumentException("Payment capture failed");
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalArgumentException("PayPal is not configured on server");
        }
    }
}
