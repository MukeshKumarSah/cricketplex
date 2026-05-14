package com.cricketplex.controller;

import com.cricketplex.dto.CreateRazorpayOrderRequest;
import com.cricketplex.dto.VerifyRazorpayPaymentRequest;
import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.RazorpayService;
import com.cricketplex.service.SupporterSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supporter")
@RequiredArgsConstructor
public class SupporterController {

    private final UserRepository userRepository;
    private final RazorpayService razorpayService;
    private final SupporterSubscriptionService supporterSubscriptionService;

    @GetMapping("/plans/india")
    public ResponseEntity<?> getIndianPlans() {
        return ResponseEntity.ok(supporterSubscriptionService.getIndianPlans());
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(supporterSubscriptionService.getSupporterStatus(user));
    }

    @PostMapping("/razorpay/order")
    public ResponseEntity<?> createRazorpayOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody CreateRazorpayOrderRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        int amountInPaise = supporterSubscriptionService.getAmountInPaise(request.getPlanCode());
        String orderId = razorpayService.createOrder(request.getPlanCode(), amountInPaise, user.getId().toString());
        supporterSubscriptionService.markOrderCreated(user, orderId, request.getPlanCode());

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "key", razorpayService.getKeyId(),
                "amount", amountInPaise,
                "currency", "INR",
                "planCode", request.getPlanCode()
        ));
    }

    @PostMapping("/razorpay/verify")
    public ResponseEntity<?> verifyRazorpayPayment(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody VerifyRazorpayPaymentRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!request.getOrderId().equals(user.getSupporterOrderId())) {
            throw new IllegalArgumentException("Order mismatch. Please retry payment.");
        }

        if (!request.getPlanCode().equals(user.getSupporterPlan())) {
            throw new IllegalArgumentException("Plan mismatch. Please retry payment.");
        }

        boolean verified = razorpayService.verifySignature(
                request.getOrderId(),
                request.getPaymentId(),
                request.getSignature()
        );
        if (!verified) {
            throw new IllegalArgumentException("Payment verification failed");
        }

        supporterSubscriptionService.activateSupporter(
                user,
                request.getPlanCode(),
                request.getOrderId(),
                request.getPaymentId()
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Supporter membership activated",
                "isSupporter", true
        ));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUsersForSupporterAdmin(@RequestParam(required = false) String q) {
        List<Map<String, Object>> users = supporterSubscriptionService.getUsersForAdmin(q);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/admin/users/{userId}/grant")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> grantSupporterByAdmin(@PathVariable UUID userId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        int months = 1;
        if (body != null && body.get("months") != null) {
            Object value = body.get("months");
            if (value instanceof Number number) {
                months = number.intValue();
            }
        }

        supporterSubscriptionService.grantSupporterByAdmin(userId, months);
        return ResponseEntity.ok(Map.of("success", true, "message", "Supporter granted"));
    }

    @PostMapping("/admin/users/{userId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> revokeSupporterByAdmin(@PathVariable UUID userId) {
        supporterSubscriptionService.revokeSupporterByAdmin(userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Supporter revoked"));
    }
}
