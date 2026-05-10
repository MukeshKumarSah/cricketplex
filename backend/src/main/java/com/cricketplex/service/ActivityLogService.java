package com.cricketplex.service;

import com.cricketplex.entity.ActivityLog;
import com.cricketplex.entity.Team;
import com.cricketplex.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;

    public void log(Team team, String type, String text) {
        activityLogRepository.save(ActivityLog.builder()
                .team(team)
                .type(type)
                .text(text)
                .build());
    }
}
