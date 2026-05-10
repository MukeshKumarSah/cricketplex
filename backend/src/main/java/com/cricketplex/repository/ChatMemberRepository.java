package com.cricketplex.repository;

import com.cricketplex.entity.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, UUID> {

    List<ChatMember> findByConversationId(UUID conversationId);

    Optional<ChatMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    void deleteByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ChatMember> findByUserId(UUID userId);
}
