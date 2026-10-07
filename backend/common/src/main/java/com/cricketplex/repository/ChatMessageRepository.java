package com.cricketplex.repository;

import com.cricketplex.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /** Count messages in a conversation after a given timestamp (for unread count) */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversation.id = :convId AND m.createdAt > :since AND m.sender.id <> :userId")
    long countUnreadMessages(@Param("convId") UUID conversationId, @Param("since") LocalDateTime since, @Param("userId") UUID userId);

    /** Total unread across all conversations for a user */
    @Query("SELECT COUNT(m) FROM ChatMessage m JOIN ChatMember cm ON cm.conversation.id = m.conversation.id " +
           "WHERE cm.user.id = :userId AND m.createdAt > cm.lastReadAt AND m.sender.id <> :userId")
    long countTotalUnread(@Param("userId") UUID userId);
}
