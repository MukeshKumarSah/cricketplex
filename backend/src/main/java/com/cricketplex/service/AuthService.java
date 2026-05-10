package com.cricketplex.service;

import com.cricketplex.dto.*;
import com.cricketplex.entity.Role;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final FileStorageService fileStorageService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public ApiResponse signup(SignupRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (request.getAcceptedTerms() == null || !request.getAcceptedTerms()) {
            throw new IllegalArgumentException("You must accept the terms and conditions");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        User user = User.builder()
                .name(request.getName())
                .username(request.getUsername())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .acceptedTerms(true)
                .emailVerified(false)
                .emailVerificationToken(UUID.randomUUID().toString().replace("-", ""))
                .emailVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24))
                .build();

        user = userRepository.save(user);
        emailVerificationService.sendVerificationEmail(user);

        return new ApiResponse(true, "Signup successful. Please verify your email before logging in.");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(request.getUsernameOrEmail().toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Please verify your email before logging in. You can request a new verification email.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()
                )
        );

        String token = tokenProvider.generateToken(user.getId(), user.getUsername());

        return buildAuthResponse(token, user);
    }

    @Transactional
    public ApiResponse verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return new ApiResponse(true, "Email is already verified. You can log in now.");
        }
        if (user.getEmailVerificationTokenExpiresAt() == null
                || user.getEmailVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification link has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiresAt(null);
        userRepository.save(user);
        return new ApiResponse(true, "Email verified successfully. You can log in now.");
    }

    @Transactional
    public ApiResponse resendVerification(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No account found for this email"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            return new ApiResponse(true, "Email is already verified. Please log in.");
        }

        user.setEmailVerificationToken(UUID.randomUUID().toString().replace("-", ""));
        user.setEmailVerificationTokenExpiresAt(LocalDateTime.now().plusHours(24));
        userRepository.save(user);
        emailVerificationService.sendVerificationEmail(user);
        return new ApiResponse(true, "Verification email sent. Please check your inbox.");
    }

    @Transactional
    public ApiResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPasswordResetToken(UUID.randomUUID().toString().replace("-", ""));
            user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);
            emailVerificationService.sendPasswordResetEmail(user);
        }
        return new ApiResponse(true, "If the email exists, a password reset link has been sent.");
    }

    @Transactional
    public ApiResponse resetPassword(ResetPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        User user = userRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));
        if (user.getPasswordResetTokenExpiresAt() == null
                || user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset link has expired. Please request a new one.");
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        userRepository.save(user);
        return new ApiResponse(true, "Password reset successful. You can log in now.");
    }

    public AuthResponse.UserInfo getCurrentUser(User user) {
        Optional<Team> teamOpt = teamRepository.findByOwner(user);
        List<Team> allTeams = teamRepository.findByOwnerOrderByTeamOrderAsc(user);
        
        return AuthResponse.UserInfo.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isSupporter(user.getIsSupporter())
                .isSubAdmin(user.getIsSubAdmin())
                .teamSetupDone(user.getTeamSetupDone())
                .emailVerified(user.getEmailVerified())
                .profilePicUrl(fileStorageService.buildFileUrl(user.getProfilePicUrl()))
                .teamId(teamOpt.map(t -> t.getId().toString()).orElse(null))
                .activeTeamId(user.getActiveTeamId() != null ? user.getActiveTeamId().toString() : teamOpt.map(t -> t.getId().toString()).orElse(null))
                .hasMultipleTeams(allTeams.size() > 1)
                .theme(user.getTheme())
                .build();
    }

    private AuthResponse buildAuthResponse(String token, User user) {
        Optional<Team> teamOpt = teamRepository.findByOwner(user);
        List<Team> allTeams = teamRepository.findByOwnerOrderByTeamOrderAsc(user);
        
        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId().toString())
                        .name(user.getName())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .isSupporter(user.getIsSupporter())
                        .isSubAdmin(user.getIsSubAdmin())
                        .teamSetupDone(user.getTeamSetupDone())
                        .emailVerified(user.getEmailVerified())
                        .profilePicUrl(fileStorageService.buildFileUrl(user.getProfilePicUrl()))
                        .teamId(teamOpt.map(t -> t.getId().toString()).orElse(null))
                        .activeTeamId(user.getActiveTeamId() != null ? user.getActiveTeamId().toString() : teamOpt.map(t -> t.getId().toString()).orElse(null))
                        .hasMultipleTeams(allTeams.size() > 1)
                        .theme(user.getTheme())
                        .build())
                .build();
    }
}
