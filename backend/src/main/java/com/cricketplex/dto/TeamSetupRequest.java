package com.cricketplex.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeamSetupRequest {

    @NotBlank(message = "Team name is required")
    private String teamName;

    @NotBlank(message = "Country is required")
    private String country;
}
