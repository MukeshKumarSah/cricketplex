package com.cricketplex.service;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupporterSubscriptionService {

    private static final Map<String, Integer> INR_AMOUNTS = Map.of(
            "MONTHLY", 15900,
            "QUARTERLY", 39900,
            "HALF_YEARLY", 69900,
            "YEARLY", 119900
    );

    private static final Map<String, Double> USD_AMOUNTS = Map.of(
            "MONTHLY", 2.0,
            "QUARTERLY", 5.5,
            "HALF_YEARLY", 10.0,
            "YEARLY", 18.0
    );

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public int getAmountInPaise(String planCode) {
        Integer amount = INR_AMOUNTS.get(planCode);
        if (amount == null) {
            throw new IllegalArgumentException("Invalid plan selected");
        }
        return amount;
    }

    public double getAmountInUsd(String planCode) {
        Double amount = USD_AMOUNTS.get(planCode);
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
                planInr("MONTHLY", "Monthly", 159),
                planInr("QUARTERLY", "3 Monthly", 399),
                planInr("HALF_YEARLY", "6 Monthly", 699),
                planInr("YEARLY", "Yearly", 1199)
        );
    }

    public List<Map<String, Object>> getGlobalPlans() {
        return List.of(
                planUsd("MONTHLY", "Monthly", 2.0),
                planUsd("QUARTERLY", "3 Monthly", 5.5),
                planUsd("HALF_YEARLY", "6 Monthly", 10.0),
                planUsd("YEARLY", "Yearly", 18.0)
        );
    }

    private Map<String, Object> planInr(String code, String label, int amountInInr) {
        return Map.of(
                "code", code,
                "label", label,
                "currency", "INR",
                "amount", amountInInr,
                "amountInPaise", amountInInr * 100
        );
    }

    private Map<String, Object> planUsd(String code, String label, double amount) {
        Map<String, Object> p = new HashMap<>();
        p.put("code", code);
        p.put("label", label);
        p.put("currency", "USD");
        p.put("amount", amount);
        return p;
    }

    @Transactional
    public void markOrderCreated(User user, String orderId, String planCode) {
        user.setSupporterOrderId(orderId);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("RAZORPAY");
        userRepository.save(user);
    }

    @Transactional
    public void markPayPalOrderCreated(User user, String orderId, String planCode) {
        user.setSupporterOrderId(orderId);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("PAYPAL");
        userRepository.save(user);
    }

    @Transactional
    public void activateSupporter(User user, String planCode, String orderId, String paymentId) {
        int months = getDurationMonths(planCode);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = effectiveBase(user, now);

        user.setIsSupporter(true);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("RAZORPAY");
        user.setSupporterOrderId(orderId);
        user.setSupporterPaymentId(paymentId);
        if (!Boolean.TRUE.equals(user.getIsSupporter()) || user.getSupporterSince() == null) {
            user.setSupporterSince(now);
        }
        user.setSupporterUntil(base.plusMonths(months));
        userRepository.save(user);
    }

    @Transactional
    public void activateSupporterPayPal(User user, String planCode, String orderId, String captureId) {
        int months = getDurationMonths(planCode);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = effectiveBase(user, now);

        user.setIsSupporter(true);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("PAYPAL");
        user.setSupporterOrderId(orderId);
        user.setSupporterPaymentId(captureId);
        if (!Boolean.TRUE.equals(user.getIsSupporter()) || user.getSupporterSince() == null) {
            user.setSupporterSince(now);
        }
        user.setSupporterUntil(base.plusMonths(months));
        userRepository.save(user);
    }

    @Transactional
    public void markStripePaymentCreated(User user, String paymentIntentId, String planCode) {
        user.setSupporterOrderId(paymentIntentId);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("STRIPE");
        userRepository.save(user);
    }

    @Transactional
    public void activateSupporterStripe(User user, String planCode, String paymentIntentId) {
        int months = getDurationMonths(planCode);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = effectiveBase(user, now);

        user.setIsSupporter(true);
        user.setSupporterPlan(planCode);
        user.setSupporterProvider("STRIPE");
        user.setSupporterOrderId(paymentIntentId);
        user.setSupporterPaymentId(paymentIntentId);
        if (!Boolean.TRUE.equals(user.getIsSupporter()) || user.getSupporterSince() == null) {
            user.setSupporterSince(now);
        }
        user.setSupporterUntil(base.plusMonths(months));
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
        LocalDateTime base = effectiveBase(user, now);
        user.setIsSupporter(true);
        user.setSupporterPlan("ADMIN_GRANT_" + months + "M");
        user.setSupporterProvider("ADMIN");
        user.setSupporterOrderId(null);
        user.setSupporterPaymentId(null);
        if (!Boolean.TRUE.equals(user.getIsSupporter()) || user.getSupporterSince() == null) {
            user.setSupporterSince(now);
        }
        user.setSupporterUntil(base.plusMonths(months));
        userRepository.save(user);
    }

    @Transactional
    public void revokeSupporterByAdmin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        revokeSupporter(user);
    }

    /** Shared revoke logic — used by admin revoke and scheduled expiry. */
    @Transactional
    public void revokeSupporter(User user) {
        user.setIsSupporter(false);
        user.setSupporterPlan(null);
        user.setSupporterProvider(null);
        user.setSupporterOrderId(null);
        user.setSupporterPaymentId(null);
        user.setSupporterSince(null);
        user.setSupporterUntil(null);
        stripSupporterPerks(user);
        userRepository.save(user);
    }

    /**
     * Strips supporter-gated perks from a user:
     * - Converts their secondary team (teamOrder=2) to a bot and removes ownership.
     * - Resets activeTeamId to their primary team if they were on the secondary one.
     */
    private void stripSupporterPerks(User user) {
        List<Team> teams = teamRepository.findByOwnerOrderByTeamOrderAsc(user);
        Team primary = teams.stream().filter(t -> t.getTeamOrder() == 1).findFirst().orElse(null);
        Team secondary = teams.stream().filter(t -> t.getTeamOrder() == 2).findFirst().orElse(null);

        if (secondary != null) {
            // If user is currently playing as the secondary team, switch them back to primary.
            if (secondary.getId().equals(user.getActiveTeamId()) && primary != null) {
                user.setActiveTeamId(primary.getId());
            }
            secondary.setIsBot(true);
            secondary.setOwner(null);
            teamRepository.save(secondary);
            log.info("Secondary team {} (owner {}) converted to bot on supporter revoke/expiry",
                    secondary.getId(), user.getId());
        }
    }

    /** Daily job — auto-expire supporters whose plan period has ended. */
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    @Transactional
    public void expireStaleSupport() {
        List<User> expired = userRepository.findExpiredSupporters(LocalDateTime.now());
        for (User user : expired) {
            try {
                revokeSupporter(user);
                log.info("Supporter plan expired and revoked for user {}", user.getId());
            } catch (Exception e) {
                log.error("Failed to expire supporter for user {}: {}", user.getId(), e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} supporter plan(s)", expired.size());
        }
    }

    private boolean containsIgnoreCase(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    /** Returns the later of now and the user's current supporterUntil (if still in the future). */
    private LocalDateTime effectiveBase(User user, LocalDateTime now) {
        if (Boolean.TRUE.equals(user.getIsSupporter())
                && user.getSupporterUntil() != null
                && user.getSupporterUntil().isAfter(now)) {
            return user.getSupporterUntil();
        }
        return now;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
