package com.cricketplex.service;

import com.cricketplex.dto.*;
import com.cricketplex.entity.Role;
import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final FileStorageService fileStorageService;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
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
                .build();

        user = userRepository.save(user);

        String token = tokenProvider.generateToken(user.getId(), user.getUsername());

        return buildAuthResponse(token, user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmail(request.getUsernameOrEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        String token = tokenProvider.generateToken(user.getId(), user.getUsername());

        return buildAuthResponse(token, user);
    }

    public AuthResponse.UserInfo getCurrentUser(User user) {
        return AuthResponse.UserInfo.builder()
                .id(user.getId().toString())
                .name(user.getName())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .isSupporter(user.getIsSupporter())
                .isSubAdmin(user.getIsSubAdmin())
                .teamSetupDone(user.getTeamSetupDone())
                .profilePicUrl(fileStorageService.buildFileUrl(user.getProfilePicUrl()))
                .build();
    }

    private AuthResponse buildAuthResponse(String token, User user) {
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
                        .profilePicUrl(fileStorageService.buildFileUrl(user.getProfilePicUrl()))
                        .build())
                .build();
    }
}
