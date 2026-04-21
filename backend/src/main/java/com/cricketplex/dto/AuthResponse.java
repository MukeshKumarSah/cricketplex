package com.cricketplex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type;
    private UserInfo user;

    @Data
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private String id;
        private String name;
        private String username;
        private String email;
        private String role;
        private Boolean isSupporter;
        private Boolean isSubAdmin;
        private Boolean teamSetupDone;
        private String profilePicUrl;
        private String teamId;
        private String theme;
    }
}
