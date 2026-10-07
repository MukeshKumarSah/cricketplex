package com.cricketplex.controller;

import com.cricketplex.entity.Role;
import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.BlogService;
import com.cricketplex.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/blogs")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    // -- List ------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<?> listBlogs(@AuthenticationPrincipal UserPrincipal user,
                                        @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(blogService.listBlogs(page, user.getId()));
    }

    // -- Detail ----------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<?> getBlog(@AuthenticationPrincipal UserPrincipal user,
                                      @PathVariable UUID id) {
        return ResponseEntity.ok(blogService.getBlog(id, user.getId()));
    }

    // -- New count -------------------------------------------------------------

    @GetMapping("/new-count")
    public ResponseEntity<?> newCount(@RequestParam(required = false) String since) {
        java.time.LocalDateTime sinceTime;
        try {
            sinceTime = since != null && !since.isBlank()
                    ? java.time.Instant.parse(since)
                              .atZone(java.time.ZoneOffset.UTC)
                              .toLocalDateTime()
                    : null;
        } catch (Exception e) {
            sinceTime = null;
        }
        long count = sinceTime != null ? blogService.newBlogCount(sinceTime) : 0L;
        return ResponseEntity.ok(java.util.Map.of("count", count));
    }

    // -- Create ----------------------------------------------------------------

    @PostMapping
    public ResponseEntity<?> createBlog(@AuthenticationPrincipal UserPrincipal user,
                                         @RequestBody Map<String, String> body) {
        requireCanWrite(user);
        Map<String, Object> result = blogService.createBlog(
                user.getId(),
                isAdmin(user),
                body.get("title"),
                body.get("description"),
                body.get("body"));
        return ResponseEntity.ok(result);
    }

    // -- Edit ------------------------------------------------------------------

    @PutMapping("/{id}")
    public ResponseEntity<?> editBlog(@AuthenticationPrincipal UserPrincipal user,
                                       @PathVariable UUID id,
                                       @RequestBody Map<String, String> body) {
        requireCanWrite(user);
        blogService.editBlog(id, user.getId(), isAdmin(user),
                body.get("title"), body.get("description"), body.get("body"));
        return ResponseEntity.ok(Map.of("message", "Blog updated"));
    }

    // -- Delete ----------------------------------------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBlog(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable UUID id) {
        requireCanWrite(user);
        blogService.deleteBlog(id, user.getId(), isAdmin(user));
        return ResponseEntity.ok(Map.of("message", "Blog deleted"));
    }

    // -- Pin -------------------------------------------------------------------

    @PostMapping("/{id}/pin")
    public ResponseEntity<?> pinBlog(@AuthenticationPrincipal UserPrincipal user,
                                      @PathVariable UUID id,
                                      @RequestBody Map<String, Boolean> body) {
        requireAdmin(user);
        boolean pin = Boolean.TRUE.equals(body.get("pinned"));
        blogService.pinBlog(id, pin);
        return ResponseEntity.ok(Map.of("message", pin ? "Blog pinned" : "Blog unpinned"));
    }

    // -- React -----------------------------------------------------------------

    @PostMapping("/{id}/react")
    public ResponseEntity<?> react(@AuthenticationPrincipal UserPrincipal user,
                                    @PathVariable UUID id,
                                    @RequestBody Map<String, String> body) {
        String reaction = body.get("reaction");
        if (reaction == null || reaction.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "reaction is required"));
        return ResponseEntity.ok(blogService.toggleReaction(id, user.getId(), reaction));
    }

    // -- Image upload ----------------------------------------------------------

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadImage(@AuthenticationPrincipal UserPrincipal user,
                                          @RequestParam("file") MultipartFile file) {
        requireCanWrite(user);
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/"))
            return ResponseEntity.badRequest().body(Map.of("error", "Only image files are allowed"));
        String objectKey = fileStorageService.uploadFile(file, "blog-images");
        String url = fileStorageService.buildFileUrl(objectKey);
        return ResponseEntity.ok(Map.of("url", url, "objectKey", objectKey));
    }

    // -- Error handling --------------------------------------------------------

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<?> handleError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    // -- Helpers ---------------------------------------------------------------

    private boolean isAdmin(UserPrincipal user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void requireAdmin(UserPrincipal user) {
        if (!isAdmin(user)) throw new IllegalStateException("Admin access required");
    }

    /** Admin OR active supporter can create/edit blogs */
    private void requireCanWrite(UserPrincipal user) {
        if (isAdmin(user)) return;
        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (!Boolean.TRUE.equals(dbUser.getIsSupporter())) {
            throw new IllegalStateException("Only admins and supporters can create or edit blogs");
        }
    }
}
