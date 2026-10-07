package com.cricketplex.controller;

import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.FriendlyTournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class FriendlyTournamentController {

    private final FriendlyTournamentService tournamentService;

    /** POST /api/tournaments — create a tournament (supporter or admin) */
    @PostMapping
    public ResponseEntity<?> createTournament(@AuthenticationPrincipal UserPrincipal user,
                                               @RequestBody Map<String, Object> body) {
        body.put("_isAdmin", isAdmin(user));
        var t = tournamentService.createTournament(user.getId(), body);
        return ResponseEntity.ok(Map.of(
                "id",       t.getId(),
                "name",     t.getName(),
                "joinCode", t.getJoinCode()
        ));
    }

    /** GET /api/tournaments?filter=public|mine|joined|pending */
    @GetMapping
    public ResponseEntity<?> listTournaments(@AuthenticationPrincipal UserPrincipal user,
                                              @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(tournamentService.listTournaments(user.getId(), filter));
    }

    /** GET /api/tournaments/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<?> getTournamentDetail(@AuthenticationPrincipal UserPrincipal user,
                                                  @PathVariable UUID id) {
        return ResponseEntity.ok(tournamentService.getTournamentDetail(id, user.getId()));
    }

    /** POST /api/tournaments/{id}/invite/{teamId} — invite a specific team */
    @PostMapping("/{id}/invite/{teamId}")
    public ResponseEntity<?> inviteTeam(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable UUID id,
                                         @PathVariable UUID teamId) {
        if (isAdmin(user)) {
            tournamentService.inviteTeamAsAdmin(id, teamId);
        } else {
            tournamentService.inviteTeam(id, user.getId(), teamId);
        }
        return ResponseEntity.ok(Map.of("message", "Team invited"));
    }

    /** POST /api/tournaments/join — join a public tournament by join code (code in body) */
    @PostMapping("/join")
    public ResponseEntity<?> joinByCode(@AuthenticationPrincipal UserPrincipal user,
                                         @RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "code is required"));
        tournamentService.joinByCode(user.getId(), code);
        return ResponseEntity.ok(Map.of("message", "Joined tournament"));
    }

    /** POST /api/tournaments/{id}/join — alias kept for backwards compat */
    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinByCodeWithId(@AuthenticationPrincipal UserPrincipal user,
                                               @PathVariable UUID id,
                                               @RequestBody Map<String, String> body) {
        return joinByCode(user, body);
    }

    /** POST /api/tournaments/{id}/respond — accept or decline an invite */
    @PostMapping("/{id}/respond")
    public ResponseEntity<?> respondToInvite(@AuthenticationPrincipal UserPrincipal user,
                                              @PathVariable UUID id,
                                              @RequestBody Map<String, Object> body) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        tournamentService.respondToInvite(user.getId(), id, accept);
        return ResponseEntity.ok(Map.of("message", accept ? "Invite accepted" : "Invite declined"));
    }

    /** POST /api/tournaments/{id}/start — lock registration and generate fixtures */
    @PostMapping("/{id}/start")
    public ResponseEntity<?> startTournament(@AuthenticationPrincipal UserPrincipal user,
                                              @PathVariable UUID id) {
        tournamentService.startTournament(id, user.getId(), isAdmin(user));
        return ResponseEntity.ok(Map.of("message", "Tournament started"));
    }

    /** POST /api/tournaments/{id}/advance — advance knockout to next round */
    @PostMapping("/{id}/advance")
    public ResponseEntity<?> advanceKnockout(@AuthenticationPrincipal UserPrincipal user,
                                              @PathVariable UUID id) {
        tournamentService.advanceKnockoutRound(id, user.getId(), isAdmin(user));
        return ResponseEntity.ok(Map.of("message", "Next round generated"));
    }

    /** DELETE /api/tournaments/{id} — cancel tournament */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelTournament(@AuthenticationPrincipal UserPrincipal user,
                                               @PathVariable UUID id) {
        tournamentService.cancelTournament(id, user.getId(), isAdmin(user));
        return ResponseEntity.ok(Map.of("message", "Tournament cancelled"));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    private boolean isAdmin(UserPrincipal user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
