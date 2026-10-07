package com.cricketplex.service.match;

import com.cricketplex.entity.*;
import com.cricketplex.repository.MatchFCStrategyRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class FirstClassSupport {
    private final MatchFCStrategyRepository matchFCStrategyRepository;

    public String buildResumeState(BatsmanState striker, BatsmanState nonStriker,
                                     int nextBatIdx, Map<UUID, Integer> bowlerBallCounts,
                                     Player previousBowler) {
        StringBuilder sb = new StringBuilder();
        sb.append(striker.player.getId()).append('|');
        sb.append(nonStriker.player.getId()).append('|');
        sb.append(nextBatIdx).append('|');
        sb.append(previousBowler != null ? previousBowler.getId().toString() : "null").append('|');
        boolean first = true;
        for (Map.Entry<UUID, Integer> e : bowlerBallCounts.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    public FCResumeState parseResumeState(String state) {
        String[] parts = state.split("\\|", -1);
        FCResumeState r = new FCResumeState();
        r.strikerId        = UUID.fromString(parts[0]);
        r.nonStrikerId     = UUID.fromString(parts[1]);
        r.nextBatIdx       = Integer.parseInt(parts[2]);
        r.previousBowlerId = "null".equals(parts[3]) ? null : UUID.fromString(parts[3]);
        if (parts.length > 4 && !parts[4].isEmpty()) {
            for (String entry : parts[4].split(",")) {
                String[] kv = entry.split("=");
                r.bowlerBallCounts.put(UUID.fromString(kv[0]), Integer.parseInt(kv[1]));
            }
        }
        return r;
    }

    public int computeBallsBowled(Double totalOvers) {
        if (totalOvers == null || totalOvers <= 0) return 0;
        int full    = (int) Math.floor(totalOvers);
        int partial = (int) Math.round((totalOvers - full) * 10);
        return full * 6 + partial;
    }

    public boolean isHumanTeam(Team team) { return team.getOwner() != null; }

    public Map<UUID, MatchFCStrategy> loadFCStrategies(Fixture fixture) {
        Map<UUID, MatchFCStrategy> byTeam = new HashMap<>();
        for (MatchFCStrategy s : matchFCStrategyRepository.findByFixtureId(fixture.getId()))
            byTeam.put(s.getTeam().getId(), s);
        return byTeam;
    }

    public Integer getDeclareInn1(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getDeclareInn1() : fixture.getFcDeclareInn1();
    }
    public Integer getDeclareInn2Lead(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getDeclareInn2Lead() : fixture.getFcDeclareInn2Lead();
    }
    public Integer getDeclareInn3Lead(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getDeclareInn3Lead() : fixture.getFcDeclareInn3Lead();
    }
    public Boolean getFollowOn(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getFollowOn() : fixture.getFcFollowOn();
    }
    public long[] computeInn3Declaration(Fixture fixture, Map<UUID, MatchFCStrategy> s,
                                           List<Innings> inningsList, Team battingTeam,
                                           Team battingFirst, Team battingSecond) {
        int i1 = inningsList.get(0).getTotalRuns();
        int i2 = inningsList.get(1).getTotalRuns();
        boolean fo    = battingTeam.getId().equals(battingSecond.getId());
        boolean human = isHumanTeam(battingTeam);
        Integer declareLead = getDeclareInn3Lead(fixture, s, battingTeam);
        if (human && declareLead != null && declareLead > 0) {
            int da = fo ? (i1 - i2 + declareLead) : (i2 - i1 + declareLead);
            return new long[]{1, Math.max(1, da)};
        }
        if (!human) {
            int aiLead = 250;
            int da = fo ? (i1 - i2 + aiLead) : (i2 - i1 + aiLead);
            return new long[]{1, Math.max(1, da)};
        }
        return new long[]{0, 0};
    }

    public int computeFCChaseTarget(List<Innings> inningsList, Team battingSecond) {
        int i1 = inningsList.get(0).getTotalRuns();
        int i2 = inningsList.get(1).getTotalRuns();
        int i3 = inningsList.get(2).getTotalRuns();
        boolean fo = inningsList.get(2).getBattingTeam().getId().equals(battingSecond.getId());
        return fo ? (i2 + i3) - i1 + 1 : (i1 + i3) - i2 + 1;
    }
    public void determineFCResult(MatchResult result, int inn1, int inn2, int inn3, int inn4,
                                    Team battingFirst, Team battingSecond, boolean followOn, boolean inningsDefeat) {
        if (inningsDefeat) {
            if (followOn) {
                int margin = inn1 - (inn2 + inn3);
                if (margin > 0) { result.setWinner(battingFirst); result.setResultType("INNINGS"); result.setResultMargin(margin); }
                else result.setResultType("DRAW");
            } else {
                int margin = inn2 - (inn1 + inn3);
                if (margin > 0) { result.setWinner(battingSecond); result.setResultType("INNINGS"); result.setResultMargin(margin); }
                else result.setResultType("DRAW");
            }
            return;
        }
        int team1Total   = followOn ? inn1 : inn1 + inn3;
        int team2Total   = followOn ? inn2 + inn3 : inn2;
        int target       = team1Total - team2Total + 1;
        Team chasingTeam = followOn ? battingFirst : battingSecond;
        Team settingTeam = followOn ? battingSecond : battingFirst;
        if (inn4 >= target) {
            Innings last = result.getInningsList().stream().filter(i -> i.getInningsNumber() == 4).findFirst().orElse(null);
            int wl = last != null ? last.getTotalWickets() : 0;
            result.setWinner(chasingTeam); result.setResultType("WICKETS"); result.setResultMargin(10 - wl);
        } else {
            Innings last = result.getInningsList().stream().filter(i -> i.getInningsNumber() == 4).findFirst().orElse(null);
            if (last != null && (Boolean.TRUE.equals(last.getAllOut()) || last.getTotalWickets() >= 10)) {
                result.setWinner(settingTeam); result.setResultType("RUNS"); result.setResultMargin(target - inn4 - 1);
            } else {
                result.setResultType("DRAW");
            }
        }
    }

    public int getOversUsed(Innings innings) {
        double totalOvers = innings.getTotalOvers() != null ? innings.getTotalOvers() : 0.0;
        int full    = (int) totalOvers;
        int partial = (int) Math.round((totalOvers - full) * 10);
        return full + (partial > 0 ? 1 : 0);
    }

}
