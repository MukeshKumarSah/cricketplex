package com.cricketplex.util;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TeamHelper {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    /**
     * Get the active team for the authenticated user.
     * Uses the activeTeamId from User if set, otherwise returns the first team found.
     */
    public Team getActiveTeam(UserPrincipal principal) {
        User user = userRepository.findById(UUID.fromString(principal.getId()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Use active team if set
        if (user.getActiveTeamId() != null) {
            return teamRepository.findById(user.getActiveTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("Active team not found"));
        }
        
        // Fall back to finding first team (for backwards compatibility)
        return teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("No team found"));
    }

    /**
     * Get the active team for a user entity.
     */
    public Team getActiveTeam(User user) {
        if (user.getActiveTeamId() != null) {
            return teamRepository.findById(user.getActiveTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("Active team not found"));
        }
        
        return teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("No team found"));
    }
}
