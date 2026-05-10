package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/finances")
@RequiredArgsConstructor
public class FinanceController {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final PlayerRepository playerRepository;

    // ════════════════════════════════════════════
    //  GET /api/finances — overview + transactions
    // ════════════════════════════════════════════
    @GetMapping
    public ResponseEntity<?> getFinances(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String type) {

        Team team = getTeam(principal);

        List<TransactionLog> transactions;
        if (type != null && !type.isEmpty()) {
            transactions = transactionLogRepository.findByTeamIdAndType(team.getId(), type);
        } else {
            transactions = transactionLogRepository.findByTeamIdOrderByCreatedAtDesc(team.getId());
        }

        Long totalIncome = transactionLogRepository.getTotalIncome(team.getId());
        Long totalExpenses = transactionLogRepository.getTotalExpenses(team.getId());

        // Calculate total wages
        List<Player> players = playerRepository.findByTeamId(team.getId());
        long totalWages = players.stream().mapToLong(Player::getWage).sum();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("balance", team.getFunds());
        resp.put("totalIncome", totalIncome);
        resp.put("totalExpenses", totalExpenses);
        resp.put("squadSize", players.size());
        resp.put("totalWages", totalWages);

        List<Map<String, Object>> txList = new ArrayList<>();
        for (TransactionLog tx : transactions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", tx.getId());
            m.put("type", tx.getType());
            m.put("description", tx.getDescription());
            m.put("amount", tx.getAmount());
            m.put("balanceAfter", tx.getBalanceAfter());
            m.put("createdAt", tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null);
            txList.add(m);
        }
        resp.put("transactions", txList);

        return ResponseEntity.ok(resp);
    }

    private Team getTeam(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("No team found"));
    }
}
