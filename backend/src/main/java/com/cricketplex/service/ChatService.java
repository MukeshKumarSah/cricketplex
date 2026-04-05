package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversationRepo;
    private final ChatMemberRepository memberRepo;
    private final ChatMessageRepository messageRepo;
    private final UserRepository userRepo;
    private final FileStorageService fileStorageService;

    /** Get or create a 1-to-1 conversation between two users */
    @Transactional
    public ChatConversation getOrCreateDirect(UUID userId1, UUID userId2) {
        return conversationRepo.findDirectConversation(userId1, userId2)
                .orElseGet(() -> {
                    User u1 = userRepo.findById(userId1).orElseThrow();
                    User u2 = userRepo.findById(userId2).orElseThrow();

                    ChatConversation conv = ChatConversation.builder()
                            .isGroup(false).createdBy(u1).build();
                    conv = conversationRepo.save(conv);

                    memberRepo.save(ChatMember.builder().conversation(conv).user(u1).build());
                    memberRepo.save(ChatMember.builder().conversation(conv).user(u2).build());
                    return conv;
                });
    }

    /** Create a group conversation (admin only) */
    @Transactional
    public ChatConversation createGroup(UUID creatorId, String name, List<UUID> memberIds) {
        User creator = userRepo.findById(creatorId).orElseThrow();

        ChatConversation conv = ChatConversation.builder()
                .isGroup(true).groupName(name).createdBy(creator).build();
        conv = conversationRepo.save(conv);

        // Creator is always admin member
        memberRepo.save(ChatMember.builder().conversation(conv).user(creator).isAdmin(true).build());

        for (UUID mid : memberIds) {
            if (mid.equals(creatorId)) continue;
            User member = userRepo.findById(mid).orElseThrow();
            memberRepo.save(ChatMember.builder().conversation(conv).user(member).build());
        }
        return conv;
    }

    /** Add a member to a group (only group admins can do this) */
    @Transactional
    public void addMember(UUID conversationId, UUID requesterId, UUID newUserId) {
        ChatConversation conv = conversationRepo.findById(conversationId).orElseThrow();
        if (!conv.getIsGroup()) throw new IllegalStateException("Cannot add members to DM");

        ChatMember requester = memberRepo.findByConversationIdAndUserId(conversationId, requesterId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (!requester.getIsAdmin()) throw new IllegalStateException("Only admins can add members");

        if (memberRepo.existsByConversationIdAndUserId(conversationId, newUserId)) return;

        User newUser = userRepo.findById(newUserId).orElseThrow();
        memberRepo.save(ChatMember.builder().conversation(conv).user(newUser).build());
    }

    /** Remove a member from a group (only group admins can do this) */
    @Transactional
    public void removeMember(UUID conversationId, UUID requesterId, UUID targetUserId) {
        ChatConversation conv = conversationRepo.findById(conversationId).orElseThrow();
        if (!conv.getIsGroup()) throw new IllegalStateException("Cannot remove members from DM");

        ChatMember requester = memberRepo.findByConversationIdAndUserId(conversationId, requesterId)
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (!requester.getIsAdmin()) throw new IllegalStateException("Only admins can remove members");

        memberRepo.deleteByConversationIdAndUserId(conversationId, targetUserId);
    }

    /** Send a message */
    @Transactional
    public ChatMessage sendMessage(UUID conversationId, UUID senderId, String content) {
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("Empty message");
        if (content.length() > 2000) throw new IllegalArgumentException("Message too long");
        if (!memberRepo.existsByConversationIdAndUserId(conversationId, senderId))
            throw new IllegalStateException("Not a member of this conversation");

        ChatConversation conv = conversationRepo.findById(conversationId).orElseThrow();
        User sender = userRepo.findById(senderId).orElseThrow();

        ChatMessage msg = ChatMessage.builder()
                .conversation(conv).sender(sender).content(content.trim()).build();
        return messageRepo.save(msg);
    }

    /** Get all conversations for a user with metadata */
    public List<Map<String, Object>> getConversations(UUID userId) {
        List<ChatConversation> convs = conversationRepo.findByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ChatConversation conv : convs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", conv.getId());
            item.put("isGroup", conv.getIsGroup());
            item.put("groupName", conv.getGroupName());

            List<ChatMember> members = memberRepo.findByConversationId(conv.getId());

            if (!conv.getIsGroup()) {
                // For DM, show the other person's name
                members.stream()
                        .filter(m -> !m.getUser().getId().equals(userId))
                        .findFirst()
                        .ifPresent(m -> {
                            item.put("name", m.getUser().getName());
                            item.put("otherUserId", m.getUser().getId());
                            item.put("profilePicUrl", fileStorageService.buildFileUrl(m.getUser().getProfilePicUrl()));
                        });
            } else {
                item.put("name", conv.getGroupName());
                item.put("memberCount", members.size());
                item.put("groupPicUrl", fileStorageService.buildFileUrl(conv.getGroupPicUrl()));
            }

            // Unread count
            ChatMember me = members.stream()
                    .filter(m -> m.getUser().getId().equals(userId)).findFirst().orElse(null);
            long unread = 0;
            if (me != null) {
                unread = messageRepo.countUnreadMessages(conv.getId(), me.getLastReadAt(), userId);
            }
            item.put("unread", unread);
            result.add(item);
        }

        // Sort: most unread first, then by name
        result.sort((a, b) -> {
            long ua = (long) a.get("unread"), ub = (long) b.get("unread");
            if (ua != ub) return Long.compare(ub, ua);
            String na = (String) a.getOrDefault("name", ""), nb = (String) b.getOrDefault("name", "");
            return na.compareToIgnoreCase(nb);
        });
        return result;
    }

    /** Get messages for a conversation and mark as read */
    @Transactional
    public List<Map<String, Object>> getMessages(UUID conversationId, UUID userId) {
        if (!memberRepo.existsByConversationIdAndUserId(conversationId, userId))
            throw new IllegalStateException("Not a member");

        // Mark as read
        ChatMember me = memberRepo.findByConversationIdAndUserId(conversationId, userId).orElseThrow();
        me.setLastReadAt(LocalDateTime.now());
        memberRepo.save(me);

        List<ChatMessage> msgs = messageRepo.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : msgs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", msg.getId());
            m.put("senderId", msg.getSender().getId());
            m.put("senderName", msg.getSender().getName());
            m.put("senderPic", fileStorageService.buildFileUrl(msg.getSender().getProfilePicUrl()));
            m.put("content", msg.getContent());
            m.put("createdAt", msg.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    /** Get total unread count for the bubble badge */
    public long getTotalUnread(UUID userId) {
        return messageRepo.countTotalUnread(userId);
    }

    /** Get group members (for group info) */
    public List<Map<String, Object>> getGroupMembers(UUID conversationId) {
        List<ChatMember> members = memberRepo.findByConversationId(conversationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMember m : members) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", m.getUser().getId());
            item.put("name", m.getUser().getName());
            item.put("profilePicUrl", fileStorageService.buildFileUrl(m.getUser().getProfilePicUrl()));
            item.put("isAdmin", m.getIsAdmin());
            result.add(item);
        }
        return result;
    }

    /** Search users for starting a chat (exclude self) */
    public List<Map<String, Object>> searchUsers(String query, UUID excludeUserId) {
        List<User> users = userRepo.findByNameContainingIgnoreCase(query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            if (u.getId().equals(excludeUserId)) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", u.getId());
            item.put("name", u.getName());
            item.put("username", u.getUsername());
            item.put("profilePicUrl", fileStorageService.buildFileUrl(u.getProfilePicUrl()));
            result.add(item);
        }
        return result;
    }
}
