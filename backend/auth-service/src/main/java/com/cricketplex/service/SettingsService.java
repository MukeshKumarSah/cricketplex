package com.cricketplex.service;

import com.cricketplex.dto.ChangePasswordRequest;
import com.cricketplex.dto.UpdateTeamRequest;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.entity.Player;
import com.cricketplex.entity.MatchResult;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.repository.PlayerRepository;
import com.cricketplex.repository.MatchResultRepository;
import com.cricketplex.util.TeamHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final PlayerRepository playerRepository;
    private final MatchResultRepository matchResultRepository;
    private final TeamHelper teamHelper;

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
        Team team = teamHelper.getActiveTeam(user)
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
        Team team = teamHelper.getActiveTeam(user)
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
        Team team = teamHelper.getActiveTeam(user).orElse(null);

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("name", user.getName() != null ? user.getName() : "");
        userMap.put("username", user.getUsername() != null ? user.getUsername() : "");
        userMap.put("email", user.getEmail() != null ? user.getEmail() : "");
        userMap.put("profilePicUrl", fileStorageService.buildFileUrl(user.getProfilePicUrl()));
        userMap.put("theme", user.getTheme() != null ? user.getTheme() : "dark");

        Map<String, Object> teamMap = new LinkedHashMap<>();
        if (team != null) {
            teamMap.put("teamName", team.getTeamName() != null ? team.getTeamName() : "");
            teamMap.put("country", team.getCountry() != null ? team.getCountry() : "");
            teamMap.put("groundName", team.getGroundName() != null ? team.getGroundName() : "");
            teamMap.put("teamProfilePicUrl", fileStorageService.buildFileUrl(team.getTeamProfilePicUrl()));
            teamMap.put("fans", team.getFans() != null ? team.getFans() : 0);
            teamMap.put("academyLevel", team.getAcademyLevel() != null ? team.getAcademyLevel() : 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userMap);
        result.put("team", teamMap);
        return result;
    }

    @Transactional
    public String updateTheme(User user, String theme) {
        if (!"dark".equals(theme) && !"light".equals(theme)) {
            throw new IllegalArgumentException("Invalid theme value");
        }
        user.setTheme(theme);
        userRepository.save(user);
        return theme;
    }

    /* ─── Dashboard Stats: Morale & Fans ─── */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats(User user) {
        Team team = teamHelper.getActiveTeam(user).orElse(null);
        if (team == null) return Map.of();

        /* Recent match record (last 10) — for display only */
        List<MatchResult> recent = matchResultRepository.findRecentByTeam(team.getId());
        int matchCount = Math.min(recent.size(), 10);
        int wins = 0, draws = 0, losses = 0;
        for (int i = 0; i < matchCount; i++) {
            MatchResult mr = recent.get(i);
            String rt = mr.getResultType();
            if ("TIE".equals(rt) || "DRAW".equals(rt) || "NO_RESULT".equals(rt)) {
                draws++;
            } else if (mr.getWinner() != null && mr.getWinner().getId().equals(team.getId())) {
                wins++;
            } else {
                losses++;
            }
        }

        /* Squad confidence — context bar */
        List<Player> players = playerRepository.findByTeamId(team.getId());
        int avgConfidence = 50;
        if (!players.isEmpty()) {
            avgConfidence = (int) Math.round(players.stream().mapToDouble(Player::getConfidence).average().orElse(50.0));
        }

        /* Facilities — context bar */
        int facilityFactor = team.getAcademyLevel() * 25;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("morale", team.getMorale());
        result.put("squadConfidenceFactor", avgConfidence);
        result.put("facilityFactor", facilityFactor);
        result.put("fans", team.getFans());
        result.put("recentWins", wins);
        result.put("recentDraws", draws);
        result.put("recentLosses", losses);
        result.put("recentMatchCount", matchCount);
        result.put("fanCeiling", 150000);
        return result;
    }
}
