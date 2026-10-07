package com.cricketplex.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class StripeService {

    @Value("${app.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.stripe.publishable-key:}")
    private String publishableKey;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    public String getPublishableKey() {
        return publishableKey == null ? "" : publishableKey;
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank()
                && publishableKey != null && !publishableKey.isBlank();
    }

    /**
     * Creates a Stripe PaymentIntent.
     * Returns a map containing "paymentIntentId" and "clientSecret".
     */
    public Map<String, String> createPaymentIntent(String planCode, double amountUsd, String userId) {
        ensureConfigured();
        try {
            long amountCents = Math.round(amountUsd * 100);
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency("usd")
                    .setDescription("CricketPlex " + planCode + " Supporter Subscription")
                    .putMetadata("userId", userId)
                    .putMetadata("planCode", planCode)
                    .addPaymentMethodType("card")
                    .build();
            PaymentIntent intent = PaymentIntent.create(params);
            return Map.of(
                    "paymentIntentId", intent.getId(),
                    "clientSecret", intent.getClientSecret()
            );
        } catch (StripeException e) {
            log.error("Stripe createPaymentIntent failed: {}", e.getMessage());
            throw new IllegalArgumentException("Unable to create Stripe payment: " + e.getMessage());
        }
    }

    /**
     * Verifies that the PaymentIntent has status "succeeded".
     */
    public boolean verifyPaymentIntent(String paymentIntentId) {
        ensureConfigured();
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            return "succeeded".equals(intent.getStatus());
        } catch (StripeException e) {
            log.error("Stripe verify failed for {}: {}", paymentIntentId, e.getMessage());
            return false;
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalArgumentException("Stripe is not configured on server");
        }
    }
}
