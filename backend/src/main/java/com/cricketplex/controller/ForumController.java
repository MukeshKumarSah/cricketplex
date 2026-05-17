package com.cricketplex.controller;

import com.cricketplex.entity.ForumCategory;
import com.cricketplex.entity.ForumComment;
import com.cricketplex.entity.ForumThread;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.FileStorageService;
import com.cricketplex.service.ForumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;
    private final FileStorageService fileStorageService;

    // ── Categories ────────────────────────────────────────────────────────────

    /** GET /api/forum/categories */
    @GetMapping("/categories")
    public ResponseEntity<?> listCategories() {
        return ResponseEntity.ok(forumService.listCategories());
    }

    /** POST /api/forum/categories  (admin only) */
    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@AuthenticationPrincipal UserPrincipal user,
                                             @RequestBody Map<String, String> body) {
        requireAdmin(user);
        String name = body.get("name");
        String desc = body.get("description");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        ForumCategory cat = forumService.createCategory(user.getId(), name, desc);
        return ResponseEntity.ok(Map.of("id", cat.getId(), "name", cat.getName()));
    }

    // ── Threads ───────────────────────────────────────────────────────────────

    /** GET /api/forum/categories/{categoryId}/threads?page=0 */
    @GetMapping("/categories/{categoryId}/threads")
    public ResponseEntity<?> listThreads(@AuthenticationPrincipal UserPrincipal user,
                                          @PathVariable UUID categoryId,
                                          @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(forumService.listThreads(categoryId, user.getId(), page));
    }

    /** POST /api/forum/categories/{categoryId}/threads */
    @PostMapping("/categories/{categoryId}/threads")
    public ResponseEntity<?> createThread(@AuthenticationPrincipal UserPrincipal user,
                                           @PathVariable UUID categoryId,
                                           @RequestBody Map<String, String> body) {
        ForumThread thread = forumService.createThread(categoryId, user.getId(),
                body.get("title"), body.get("body"));
        return ResponseEntity.ok(Map.of("id", thread.getId(), "title", thread.getTitle()));
    }

    /** PUT /api/forum/threads/{threadId}/body — edit own thread body */
    @PutMapping("/threads/{threadId}/body")
    public ResponseEntity<?> editThreadBody(@AuthenticationPrincipal UserPrincipal user,
                                             @PathVariable UUID threadId,
                                             @RequestBody Map<String, String> body) {
        forumService.editThreadBody(threadId, user.getId(), body.get("body"));
        return ResponseEntity.ok(Map.of("message", "Thread updated"));
    }

    // ── Thread detail ─────────────────────────────────────────────────────────

    /** GET /api/forum/threads/{threadId} */
    @GetMapping("/threads/{threadId}")
    public ResponseEntity<?> getThread(@AuthenticationPrincipal UserPrincipal user,
                                        @PathVariable UUID threadId) {
        return ResponseEntity.ok(forumService.getThread(threadId, user.getId()));
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    /** POST /api/forum/threads/{threadId}/comments */
    @PostMapping("/threads/{threadId}/comments")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable UUID threadId,
                                         @RequestBody Map<String, String> body) {
        ForumComment comment = forumService.addComment(threadId, user.getId(), body.get("body"));
        return ResponseEntity.ok(Map.of("id", comment.getId()));
    }

    /** PUT /api/forum/comments/{commentId} — edit own comment */
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<?> editComment(@AuthenticationPrincipal UserPrincipal user,
                                          @PathVariable UUID commentId,
                                          @RequestBody Map<String, String> body) {
        forumService.editComment(commentId, user.getId(), body.get("body"));
        return ResponseEntity.ok(Map.of("message", "Comment updated"));
    }

    /** DELETE /api/forum/comments/{commentId} — own comment or admin */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@AuthenticationPrincipal UserPrincipal user,
                                            @PathVariable UUID commentId) {
        forumService.deleteComment(commentId, user.getId(), isAdmin(user));
        return ResponseEntity.ok(Map.of("message", "Comment deleted"));
    }

    // ── Image upload ──────────────────────────────────────────────────────────

    /** POST /api/forum/upload-image */
    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@AuthenticationPrincipal UserPrincipal user,
                                          @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
        String objectKey = fileStorageService.uploadFile(file, "forum-images");
        String url = fileStorageService.buildFileUrl(objectKey);
        return ResponseEntity.ok(Map.of("url", url, "objectKey", objectKey));
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    /** GET /api/forum/panel */
    @GetMapping("/panel")
    public ResponseEntity<?> getPanel() {
        return ResponseEntity.ok(forumService.getPanel());
    }

    // ── Admin actions ─────────────────────────────────────────────────────────

    /** POST /api/forum/threads/{threadId}/pin */
    @PostMapping("/threads/{threadId}/pin")
    public ResponseEntity<?> pinThread(@AuthenticationPrincipal UserPrincipal user,
                                        @PathVariable UUID threadId,
                                        @RequestBody Map<String, Boolean> body) {
        requireAdmin(user);
        boolean pin = Boolean.TRUE.equals(body.get("pinned"));
        forumService.pinThread(threadId, pin);
        return ResponseEntity.ok(Map.of("message", pin ? "Thread pinned" : "Thread unpinned"));
    }

    /** POST /api/forum/threads/{threadId}/lock */
    @PostMapping("/threads/{threadId}/lock")
    public ResponseEntity<?> lockThread(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable UUID threadId,
                                         @RequestBody Map<String, Boolean> body) {
        requireAdmin(user);
        boolean lock = Boolean.TRUE.equals(body.get("locked"));
        forumService.lockThread(threadId, lock);
        return ResponseEntity.ok(Map.of("message", lock ? "Thread locked" : "Thread unlocked"));
    }

    /** DELETE /api/forum/threads/{threadId} */
    @DeleteMapping("/threads/{threadId}")
    public ResponseEntity<?> deleteThread(@AuthenticationPrincipal UserPrincipal user,
                                           @PathVariable UUID threadId) {
        requireAdmin(user);
        forumService.deleteThread(threadId);
        return ResponseEntity.ok(Map.of("message", "Thread deleted"));
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAdmin(UserPrincipal user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void requireAdmin(UserPrincipal user) {
        if (!isAdmin(user)) throw new IllegalStateException("Admin access required");
    }
}

    /** GET /api/forum/categories */
    @GetMapping("/categories")
    public ResponseEntity<?> listCategories() {
        return ResponseEntity.ok(forumService.listCategories());
    }

    /** POST /api/forum/categories  (admin only) */
    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@AuthenticationPrincipal UserPrincipal user,
                                             @RequestBody Map<String, String> body) {
        requireAdmin(user);
        String name = body.get("name");
        String desc = body.get("description");
        if (name == null || name.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        ForumCategory cat = forumService.createCategory(user.getId(), name, desc);
        return ResponseEntity.ok(Map.of("id", cat.getId(), "name", cat.getName()));
    }

    // ── Threads ───────────────────────────────────────────────────────────────

    /** GET /api/forum/categories/{categoryId}/threads?page=0 */
    @GetMapping("/categories/{categoryId}/threads")
    public ResponseEntity<?> listThreads(@AuthenticationPrincipal UserPrincipal user,
                                          @PathVariable UUID categoryId,
                                          @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(forumService.listThreads(categoryId, user.getId(), page));
    }

    /** POST /api/forum/categories/{categoryId}/threads */
    @PostMapping("/categories/{categoryId}/threads")
    public ResponseEntity<?> createThread(@AuthenticationPrincipal UserPrincipal user,
                                           @PathVariable UUID categoryId,
                                           @RequestBody Map<String, String> body) {
        ForumThread thread = forumService.createThread(categoryId, user.getId(),
                body.get("title"), body.get("body"));
        return ResponseEntity.ok(Map.of("id", thread.getId(), "title", thread.getTitle()));
    }

    // ── Thread detail ─────────────────────────────────────────────────────────

    /** GET /api/forum/threads/{threadId} */
    @GetMapping("/threads/{threadId}")
    public ResponseEntity<?> getThread(@AuthenticationPrincipal UserPrincipal user,
                                        @PathVariable UUID threadId) {
        return ResponseEntity.ok(forumService.getThread(threadId, user.getId()));
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    /** POST /api/forum/threads/{threadId}/comments */
    @PostMapping("/threads/{threadId}/comments")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable UUID threadId,
                                         @RequestBody Map<String, String> body) {
        ForumComment comment = forumService.addComment(threadId, user.getId(), body.get("body"));
        return ResponseEntity.ok(Map.of("id", comment.getId()));
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    /** GET /api/forum/panel */
    @GetMapping("/panel")
    public ResponseEntity<?> getPanel() {
        return ResponseEntity.ok(forumService.getPanel());
    }

    // ── Admin actions ─────────────────────────────────────────────────────────

    /** POST /api/forum/threads/{threadId}/pin */
    @PostMapping("/threads/{threadId}/pin")
    public ResponseEntity<?> pinThread(@AuthenticationPrincipal UserPrincipal user,
                                        @PathVariable UUID threadId,
                                        @RequestBody Map<String, Boolean> body) {
        requireAdminOrSubAdmin(user);
        boolean pin = Boolean.TRUE.equals(body.get("pinned"));
        forumService.pinThread(threadId, pin);
        return ResponseEntity.ok(Map.of("message", pin ? "Thread pinned" : "Thread unpinned"));
    }

    /** POST /api/forum/threads/{threadId}/lock */
    @PostMapping("/threads/{threadId}/lock")
    public ResponseEntity<?> lockThread(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable UUID threadId,
                                         @RequestBody Map<String, Boolean> body) {
        requireAdminOrSubAdmin(user);
        boolean lock = Boolean.TRUE.equals(body.get("locked"));
        forumService.lockThread(threadId, lock);
        return ResponseEntity.ok(Map.of("message", lock ? "Thread locked" : "Thread unlocked"));
    }

    /** DELETE /api/forum/threads/{threadId} */
    @DeleteMapping("/threads/{threadId}")
    public ResponseEntity<?> deleteThread(@AuthenticationPrincipal UserPrincipal user,
                                           @PathVariable UUID threadId) {
        requireAdminOrSubAdmin(user);
        forumService.deleteThread(threadId);
        return ResponseEntity.ok(Map.of("message", "Thread deleted"));
    }

    /** DELETE /api/forum/comments/{commentId} */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@AuthenticationPrincipal UserPrincipal user,
                                            @PathVariable UUID commentId) {
        requireAdminOrSubAdmin(user);
        forumService.deleteComment(commentId);
        return ResponseEntity.ok(Map.of("message", "Comment deleted"));
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireAdmin(UserPrincipal user) {
        boolean isAdmin = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) throw new IllegalStateException("Admin access required");
    }

    private void requireAdminOrSubAdmin(UserPrincipal user) {
        // Sub-admins are also granted moderation rights over the forum.
        // Full admin check; sub-admin role is checked at service layer via User entity.
        requireAdmin(user);
    }
}
