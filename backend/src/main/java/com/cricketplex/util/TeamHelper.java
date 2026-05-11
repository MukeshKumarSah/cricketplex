package com.cricketplex.util;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
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
    public Optional<Team> getActiveTeam(UserPrincipal principal) {
        Optional<User> userOpt = userRepository.findById(principal.getId());
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        
        User user = userOpt.get();
        
        // Use active team if set
        if (user.getActiveTeamId() != null) {
            return teamRepository.findById(user.getActiveTeamId());
        }
        
        // Fall back to finding first team (for backwards compatibility)
        return teamRepository.findByOwnerOrderByTeamOrderAsc(user)
                .stream()
                .findFirst();
    }

    /**
     * Get the active team for a user entity.
     */
    public Optional<Team> getActiveTeam(User user) {
        if (user.getActiveTeamId() != null) {
            return teamRepository.findById(user.getActiveTeamId());
        }
        
        return teamRepository.findByOwnerOrderByTeamOrderAsc(user)
                .stream()
                .findFirst();
    }
}
