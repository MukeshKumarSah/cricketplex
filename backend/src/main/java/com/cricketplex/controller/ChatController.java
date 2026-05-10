package com.cricketplex.controller;

import com.cricketplex.entity.ChatConversation;
import com.cricketplex.entity.ChatMember;
import com.cricketplex.entity.ChatMessage;
import com.cricketplex.repository.ChatConversationRepository;
import com.cricketplex.repository.ChatMemberRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.ChatService;
import com.cricketplex.service.FileStorageService;
import com.cricketplex.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMemberRepository memberRepo;
    private final ChatConversationRepository conversationRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    /** Get all conversations for the current user */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(chatService.getConversations(user.getId()));
    }

    /** Get total unread count (for the floating bubble badge) */
    @GetMapping("/unread")
    public ResponseEntity<?> getUnreadCount(@AuthenticationPrincipal UserPrincipal user) {
        return ResponseEntity.ok(Map.of("count", chatService.getTotalUnread(user.getId())));
    }

    /** Get or create a DM conversation with another user */
    @PostMapping("/dm/{otherUserId}")
    public ResponseEntity<?> getOrCreateDM(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID otherUserId) {
        ChatConversation conv = chatService.getOrCreateDirect(user.getId(), otherUserId);
        return ResponseEntity.ok(Map.of("conversationId", conv.getId()));
    }

    /** Create a group conversation (admin only) */
    @PostMapping("/group")
    public ResponseEntity<?> createGroup(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, Object> body) {
        if (!"ADMIN".equals(user.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""))) {
            return ResponseEntity.status(403).body(Map.of("error", "Only admins can create groups"));
        }
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        List<String> memberIdStrs = (List<String>) body.get("memberIds");
        List<UUID> memberIds = memberIdStrs.stream().map(UUID::fromString).toList();

        ChatConversation conv = chatService.createGroup(user.getId(), name, memberIds);

        // Notify each non-creator member that they were added to the group
        for (UUID memberId : memberIds) {
            if (memberId.equals(user.getId())) continue;
            notificationService.create(
                    memberId,
                    "ADDED_TO_GROUP",
                    "Added to Group Chat",
                    "You've been added to the group \"" + name + "\"",
                    null
            );
        }

        return ResponseEntity.ok(Map.of("conversationId", conv.getId(), "name", name));
    }

    /** Add member to a group */
    @PostMapping("/group/{conversationId}/add/{userId}")
    public ResponseEntity<?> addMember(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID conversationId,
            @PathVariable UUID userId) {
        chatService.addMember(conversationId, user.getId(), userId);

        // Notify the newly added user
        ChatConversation conv = conversationRepo.findById(conversationId).orElse(null);
        String groupName = (conv != null && conv.getGroupName() != null) ? conv.getGroupName() : "a group";
        notificationService.create(
                userId,
                "ADDED_TO_GROUP",
                "Added to Group Chat",
                "You've been added to the group \"" + groupName + "\"",
                null
        );

        return ResponseEntity.ok(Map.of("status", "added"));
    }

    /** Remove member from a group */
    @DeleteMapping("/group/{conversationId}/remove/{userId}")
    public ResponseEntity<?> removeMember(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID conversationId,
            @PathVariable UUID userId) {
        chatService.removeMember(conversationId, user.getId(), userId);
        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    /** Get group members */
    @GetMapping("/group/{conversationId}/members")
    public ResponseEntity<?> getGroupMembers(@PathVariable UUID conversationId) {
        return ResponseEntity.ok(chatService.getGroupMembers(conversationId));
    }

    /** Get messages for a conversation (marks as read) */
    @GetMapping("/messages/{conversationId}")
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID conversationId) {
        return ResponseEntity.ok(chatService.getMessages(conversationId, user.getId()));
    }

    /** Send a message (REST fallback — also pushes via WebSocket) */
    @PostMapping("/messages/{conversationId}")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID conversationId,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        ChatMessage msg = chatService.sendMessage(conversationId, user.getId(), content);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", msg.getId());
        payload.put("conversationId", conversationId);
        payload.put("senderId", user.getId());
        payload.put("senderName", user.getName());
        payload.put("content", msg.getContent());
        payload.put("createdAt", msg.getCreatedAt());

        // Push to all members of the conversation via WebSocket
        List<ChatMember> members = memberRepo.findByConversationId(conversationId);
        for (ChatMember m : members) {
            messagingTemplate.convertAndSend("/topic/chat/" + m.getUser().getId(), payload);
        }

        return ResponseEntity.ok(payload);
    }

    /** Search users for starting a new chat */
    @GetMapping("/users/search")
    public ResponseEntity<?> searchUsers(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam String q) {
        return ResponseEntity.ok(chatService.searchUsers(q, user.getId()));
    }

    /** Upload group profile pic (admin only) */
    @PostMapping("/group/{conversationId}/pic")
    public ResponseEntity<?> uploadGroupPic(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable UUID conversationId,
            @RequestParam("file") MultipartFile file) {
        ChatMember member = memberRepo.findByConversationIdAndUserId(conversationId, user.getId())
                .orElseThrow(() -> new IllegalStateException("Not a member"));
        if (!member.getIsAdmin()) {
            return ResponseEntity.status(403).body(Map.of("error", "Only group admins can change the picture"));
        }
        String objectKey = fileStorageService.uploadFile(file, "group-pics");
        ChatConversation conv = conversationRepo.findById(conversationId).orElseThrow();
        conv.setGroupPicUrl(objectKey);
        conversationRepo.save(conv);
        return ResponseEntity.ok(Map.of("groupPicUrl", fileStorageService.buildFileUrl(objectKey)));
    }
}
