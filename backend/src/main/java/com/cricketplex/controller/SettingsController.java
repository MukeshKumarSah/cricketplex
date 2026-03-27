package com.cricketplex.controller;

import com.cricketplex.dto.ApiResponse;
import com.cricketplex.dto.ChangePasswordRequest;
import com.cricketplex.dto.UpdateTeamRequest;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getSettings(@AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        return ResponseEntity.ok(settingsService.getSettings(user));
    }

    @PostMapping(value = "/profile-pic", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProfilePic(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestParam("file") MultipartFile file) {
        validateImage(file);
        User user = getUser(principal);
        String url = settingsService.uploadProfilePic(user, file);
        return ResponseEntity.ok(Map.of("success", true, "profilePicUrl", url));
    }

    @PostMapping(value = "/team-pic", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTeamPic(@AuthenticationPrincipal UserPrincipal principal,
                                           @RequestParam("file") MultipartFile file) {
        validateImage(file);
        User user = getUser(principal);
        String url = settingsService.uploadTeamProfilePic(user, file);
        return ResponseEntity.ok(Map.of("success", true, "teamProfilePicUrl", url));
    }

    @PutMapping("/team")
    public ResponseEntity<?> updateTeam(@AuthenticationPrincipal UserPrincipal principal,
                                        @RequestBody UpdateTeamRequest request) {
        User user = getUser(principal);
        Team team = settingsService.updateTeamDetails(user, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Team updated successfully",
                "team", Map.of(
                        "teamName", team.getTeamName(),
                        "groundName", team.getGroundName() != null ? team.getGroundName() : "",
                        "country", team.getCountry()
                )
        ));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                                      @Valid @RequestBody ChangePasswordRequest request) {
        User user = getUser(principal);
        settingsService.changePassword(user, request);
        return ResponseEntity.ok(new ApiResponse(true, "Password changed successfully"));
    }

    private User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size must be less than 5MB");
        }
    }
}
