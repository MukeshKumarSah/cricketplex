package com.cricketplex.repository;

import com.cricketplex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
