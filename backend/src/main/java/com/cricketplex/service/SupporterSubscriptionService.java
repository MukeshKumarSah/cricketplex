package com.cricketplex.service;

import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SupporterSubscriptionService {

    private static final Map<String, Integer> INR_AMOUNTS = Map.of(
            "MONTHLY", 15900,
            "QUARTERLY", 39900,
            "HALF_YEARLY", 69900,
            "YEARLY", 119900
    );

    private final UserRepository userRepository;

    public int getAmountInPaise(String planCode) {
        Integer amount = INR_AMOUNTS.get(planCode);
        if (amount == null) {
            throw new IllegalArgumentException("Invalid plan selected");
        }
        return amount;
    }

    public int getDurationMonths(String planCode) {
        return switch (planCode) {
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "HALF_YEARLY" -> 6;
            case "YEARLY" -> 12;
            default -> throw new IllegalArgumentException("Invalid plan selected");
        };
    }

    public List<Map<String, Object>> getIndianPlans() {
        return List.of(
                plan("MONTHLY", "Monthly", 159),
                plan("QUARTERLY", "3 Monthly", 399),
                plan("HALF_YEARLY", "6 Monthly", 699),
                plan("YEARLY", "Yearly", 1199)
        );
    }

    private Map<String, Object> plan(String code, String label, int amountInInr) {
        return Map.of(
                "code", code,
                "label", label,
                "currency", "INR",
                "amount", amountInInr,
                "amountInPaise", amountInInr * 100
        );
    }

    @Transactional
    public void markOrderCreated(User user, String orderId, String planCode) {
        user.setSupporterOrderId(orderId);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("RAZORPAY");
        userRepository.save(user);
    }

    @Transactional
    public void activateSupporter(User user, String planCode, String orderId, String paymentId) {
        int months = getDurationMonths(planCode);
        LocalDateTime now = LocalDateTime.now();

        user.setIsSupporter(true);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("RAZORPAY");
        user.setSupporterOrderId(orderId);
        user.setSupporterPaymentId(paymentId);
        user.setSupporterSince(now);
        user.setSupporterUntil(now.plusMonths(months));
        userRepository.save(user);
    }

    public Map<String, Object> getSupporterStatus(User user) {
        Map<String, Object> status = new HashMap<>();
        status.put("isSupporter", Boolean.TRUE.equals(user.getIsSupporter()));
        status.put("planCode", user.getSupporterPlan());
        status.put("provider", user.getSupporterProvider());
        status.put("supporterSince", user.getSupporterSince());
        status.put("supporterUntil", user.getSupporterUntil());
        return status;
    }

    public List<Map<String, Object>> getUsersForAdmin(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        Stream<User> stream = userRepository.findAll().stream();

        if (!q.isEmpty()) {
            stream = stream.filter(user ->
                    containsIgnoreCase(user.getName(), q)
                            || containsIgnoreCase(user.getUsername(), q)
                            || containsIgnoreCase(user.getEmail(), q)
            );
        }

        return stream
            .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(200)
            .map(user -> {
                Map<String, Object> row = new HashMap<>();
                row.put("id", user.getId());
                row.put("name", safe(user.getName()));
                row.put("username", safe(user.getUsername()));
                row.put("email", safe(user.getEmail()));
                row.put("isSupporter", Boolean.TRUE.equals(user.getIsSupporter()));
                row.put("supporterPlan", safe(user.getSupporterPlan()));
                row.put("supporterProvider", safe(user.getSupporterProvider()));
                row.put("supporterUntil", user.getSupporterUntil());
                return row;
            })
            .toList();
    }

    @Transactional
    public void grantSupporterByAdmin(UUID userId, int months) {
        if (months < 1 || months > 24) {
            throw new IllegalArgumentException("Months must be between 1 and 24");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        LocalDateTime now = LocalDateTime.now();
        user.setIsSupporter(true);
        user.setSupporterPlan("ADMIN_GRANT_" + months + "M");
        user.setSupporterProvider("ADMIN");
        user.setSupporterOrderId(null);
        user.setSupporterPaymentId(null);
        user.setSupporterSince(now);
        user.setSupporterUntil(now.plusMonths(months));
        userRepository.save(user);
    }

    @Transactional
    public void revokeSupporterByAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setIsSupporter(false);
        user.setSupporterPlan(null);
        user.setSupporterProvider(null);
        user.setSupporterOrderId(null);
        user.setSupporterPaymentId(null);
        user.setSupporterSince(null);
        user.setSupporterUntil(null);
        userRepository.save(user);
    }

    private boolean containsIgnoreCase(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
