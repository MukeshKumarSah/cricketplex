package com.cricketplex.repository;

import com.cricketplex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailVerificationToken(String emailVerificationToken);
    Optional<User> findByPasswordResetToken(String passwordResetToken);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    List<User> findByNameContainingIgnoreCase(String name);

    @Query("SELECT u FROM User u WHERE u.isSupporter = true AND u.supporterUntil IS NOT NULL AND u.supporterUntil < :now")
    List<User> findExpiredSupporters(@Param("now") LocalDateTime now);
}
