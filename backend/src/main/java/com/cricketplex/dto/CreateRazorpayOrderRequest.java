package com.cricketplex.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRazorpayOrderRequest {

    @NotBlank
    private String planCode;
}
