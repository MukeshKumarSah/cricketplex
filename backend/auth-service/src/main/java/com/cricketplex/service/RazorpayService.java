package com.cricketplex.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class RazorpayService {

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.key-secret}")
    private String keySecret;

    public String getKeyId() {
        return keyId;
    }

    public String createOrder(String planCode, int amountInPaise, String userId) {
        ensureConfigured();
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject req = new JSONObject();
            req.put("amount", amountInPaise);
            req.put("currency", "INR");
            req.put("receipt", "cpx_" + userId.substring(0, Math.min(8, userId.length())) + "_" + System.currentTimeMillis());

            JSONObject notes = new JSONObject();
            notes.put("planCode", planCode);
            notes.put("userId", userId);
            req.put("notes", notes);

            Order order = client.orders.create(req);
            return order.get("id").toString();
        } catch (RazorpayException ex) {
            log.error("Failed to create Razorpay order", ex);
            throw new IllegalArgumentException("Unable to create payment order right now");
        }
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        ensureConfigured();
        try {
            String payload = orderId + "|" + paymentId;
            Mac sha256 = Mac.getInstance("HmacSHA256");
            sha256.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = sha256.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().equals(signature);
        } catch (Exception ex) {
            log.error("Failed to verify Razorpay signature", ex);
            return false;
        }
    }

    private void ensureConfigured() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalArgumentException("Razorpay is not configured on server");
        }
    }
}
