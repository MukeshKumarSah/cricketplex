package com.cricketplex.dto;

import lombok.Data;

@Data
public class UpdateTeamRequest {
    private String teamName;
    private String groundName;
}
