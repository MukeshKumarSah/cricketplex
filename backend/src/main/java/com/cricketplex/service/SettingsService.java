package com.cricketplex.service;

import com.cricketplex.dto.ChangePasswordRequest;
import com.cricketplex.dto.UpdateTeamRequest;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String uploadProfilePic(User user, MultipartFile file) {
        if (user.getProfilePicUrl() != null) {
            fileStorageService.deleteFile(user.getProfilePicUrl());
        }
        String objectKey = fileStorageService.uploadFile(file, "profile-pics");
        user.setProfilePicUrl(objectKey);
        userRepository.save(user);
        return fileStorageService.buildFileUrl(objectKey);
    }

    @Transactional
    public String uploadTeamProfilePic(User user, MultipartFile file) {
        Team team = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
        if (team.getTeamProfilePicUrl() != null) {
            fileStorageService.deleteFile(team.getTeamProfilePicUrl());
        }
        String objectKey = fileStorageService.uploadFile(file, "team-pics");
        team.setTeamProfilePicUrl(objectKey);
        teamRepository.save(team);
        return fileStorageService.buildFileUrl(objectKey);
    }

    @Transactional
    public Team updateTeamDetails(User user, UpdateTeamRequest request) {
        Team team = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        if (request.getTeamName() != null && !request.getTeamName().isBlank()) {
            if (!team.getTeamName().equals(request.getTeamName())
                    && teamRepository.existsByTeamName(request.getTeamName())) {
                throw new IllegalArgumentException("Team name is already taken");
            }
            team.setTeamName(request.getTeamName());
        }

        if (request.getGroundName() != null) {
            team.setGroundName(request.getGroundName());
        }

        return teamRepository.save(team);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("New passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSettings(User user) {
        Team team = teamRepository.findByOwner(user).orElse(null);

        return Map.of(
                "user", Map.of(
                        "name", user.getName(),
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "profilePicUrl", fileStorageService.buildFileUrl(user.getProfilePicUrl())
                ),
                "team", team != null ? Map.of(
                        "teamName", team.getTeamName(),
                        "country", team.getCountry(),
                        "groundName", team.getGroundName() != null ? team.getGroundName() : "",
                        "teamProfilePicUrl", fileStorageService.buildFileUrl(team.getTeamProfilePicUrl()),
                        "fans", team.getFans()
                ) : Map.of()
        );
    }
}
