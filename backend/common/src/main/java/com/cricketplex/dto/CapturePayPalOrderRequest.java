package com.cricketplex.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CapturePayPalOrderRequest {

    @NotBlank
    private String orderId;
}
