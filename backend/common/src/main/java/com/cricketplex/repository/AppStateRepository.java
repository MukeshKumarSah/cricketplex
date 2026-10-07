package com.cricketplex.repository;

import com.cricketplex.entity.AppState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppStateRepository extends JpaRepository<AppState, String> {
}
