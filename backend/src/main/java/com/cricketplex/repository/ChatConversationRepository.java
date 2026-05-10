package com.cricketplex.repository;

import com.cricketplex.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    /** Find the 1-to-1 conversation between exactly these two users */
    @Query("SELECT c FROM ChatConversation c WHERE c.isGroup = false AND c.id IN " +
           "(SELECT m1.conversation.id FROM ChatMember m1 WHERE m1.user.id = :u1) AND c.id IN " +
           "(SELECT m2.conversation.id FROM ChatMember m2 WHERE m2.user.id = :u2)")
    Optional<ChatConversation> findDirectConversation(@Param("u1") UUID user1, @Param("u2") UUID user2);

    /** All conversations a user is a member of */
    @Query("SELECT c FROM ChatConversation c WHERE c.id IN " +
           "(SELECT m.conversation.id FROM ChatMember m WHERE m.user.id = :userId) ORDER BY c.createdAt DESC")
    List<ChatConversation> findByUserId(@Param("userId") UUID userId);
}
