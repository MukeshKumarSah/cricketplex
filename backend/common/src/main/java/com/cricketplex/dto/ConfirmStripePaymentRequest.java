package com.cricketplex.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmStripePaymentRequest {

    @NotBlank
    private String paymentIntentId;
}
