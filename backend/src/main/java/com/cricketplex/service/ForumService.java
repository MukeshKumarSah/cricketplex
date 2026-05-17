package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ForumService {

    private final ForumCategoryRepository categoryRepo;
    private final ForumThreadRepository threadRepo;
    private final ForumCommentRepository commentRepo;
    private final ForumThreadReadRepository readRepo;
    private final UserRepository userRepo;

    // ── Categories ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCategories() {
        List<ForumCategory> cats = categoryRepo.findAllByOrderByDisplayOrderAsc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ForumCategory c : cats) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",           c.getId());
            m.put("name",         c.getName());
            m.put("description",  c.getDescription());
            m.put("displayOrder", c.getDisplayOrder());
            m.put("threadCount",  threadRepo.countByCategory(c));

            // Latest thread in this category
            Page<ForumThread> latest = threadRepo.findByCategoryOrderByIsPinnedDescLastActivityAtDesc(
                    c, PageRequest.of(0, 1));
            if (latest.hasContent()) {
                ForumThread t = latest.getContent().get(0);
                Map<String, Object> lt = new LinkedHashMap<>();
                lt.put("id",           t.getId());
                lt.put("title",        t.getTitle());
                lt.put("createdAt",    t.getCreatedAt());
                lt.put("authorName",   t.getCreatedBy().getName());
                m.put("latestThread", lt);
            } else {
                m.put("latestThread", null);
            }
            result.add(m);
        }
        return result;
    }

    @Transactional
    public ForumCategory createCategory(UUID creatorId, String name, String description) {
        User creator = userRepo.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        int maxOrder = categoryRepo.findAllByOrderByDisplayOrderAsc()
                .stream().mapToInt(ForumCategory::getDisplayOrder).max().orElse(0);
        ForumCategory cat = ForumCategory.builder()
                .name(name.trim())
                .description(description != null ? description.trim() : null)
                .displayOrder(maxOrder + 1)
                .createdBy(creator)
                .build();
        return categoryRepo.save(cat);
    }

    // ── Threads ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> listThreads(UUID categoryId, UUID userId, int page) {
        ForumCategory cat = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Page<ForumThread> threadPage = threadRepo.findByCategoryOrderByIsPinnedDescLastActivityAtDesc(
                cat, PageRequest.of(page, 20));

        List<UUID> threadIds = threadPage.getContent().stream()
                .map(ForumThread::getId).toList();

        // Batch-fetch read records for this user
        Map<UUID, LocalDateTime> readMap = new HashMap<>();
        if (!threadIds.isEmpty()) {
            readRepo.findByUserIdAndThreadIdIn(userId, threadIds)
                    .forEach(r -> readMap.put(r.getId().getThreadId(), r.getLastReadAt()));
        }

        List<Map<String, Object>> threads = new ArrayList<>();
        for (ForumThread t : threadPage.getContent()) {
            Map<String, Object> m = buildThreadSummary(t, user, readMap.get(t.getId()));
            threads.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category",    Map.of("id", cat.getId(), "name", cat.getName(), "description", Optional.ofNullable(cat.getDescription()).orElse("")));
        result.put("threads",     threads);
        result.put("page",        page);
        result.put("totalPages",  threadPage.getTotalPages());
        result.put("totalThreads", threadPage.getTotalElements());
        return result;
    }

    @Transactional
    public ForumThread createThread(UUID categoryId, UUID userId, String title, String body) {
        ForumCategory cat = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        if (body  == null || body.isBlank())  throw new IllegalArgumentException("Body is required");

        ForumThread thread = ForumThread.builder()
                .category(cat)
                .title(title.trim())
                .body(body.trim())
                .createdBy(user)
                .build();
        return threadRepo.save(thread);
    }

    // ── Thread Detail ─────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> getThread(UUID threadId, UUID userId) {
        ForumThread thread = threadRepo.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Mark thread as read (upsert)
        ForumThreadReadId readId = new ForumThreadReadId(userId, threadId);
        ForumThreadRead read = readRepo.findById(readId)
                .orElseGet(() -> ForumThreadRead.builder().id(readId).build());
        read.setLastReadAt(LocalDateTime.now());
        readRepo.save(read);

        List<ForumComment> comments = commentRepo.findByThreadOrderByCreatedAtAsc(thread);

        List<Map<String, Object>> commentList = new ArrayList<>();
        for (ForumComment c : comments) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id",          c.getId());
            cm.put("body",        c.getBody());
            cm.put("authorName",  c.getCreatedBy().getName());
            cm.put("authorUsername", c.getCreatedBy().getUsername());
            cm.put("authorProfilePicUrl", c.getCreatedBy().getProfilePicUrl());
            cm.put("isOwnComment", c.getCreatedBy().getId().equals(userId));
            cm.put("createdAt",   c.getCreatedAt());
            cm.put("updatedAt",   c.getUpdatedAt());
            commentList.add(cm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id",           thread.getId());
        result.put("title",        thread.getTitle());
        result.put("body",         thread.getBody());
        result.put("isPinned",     thread.getIsPinned());
        result.put("isLocked",     thread.getIsLocked());
        result.put("commentCount", thread.getCommentCount());
        result.put("createdAt",    thread.getCreatedAt());
        result.put("lastActivityAt", thread.getLastActivityAt());
        result.put("authorName",   thread.getCreatedBy().getName());
        result.put("authorUsername", thread.getCreatedBy().getUsername());
        result.put("authorProfilePicUrl", thread.getCreatedBy().getProfilePicUrl());
        result.put("isOwnThread",  thread.getCreatedBy().getId().equals(userId));
        result.put("category",     Map.of("id", thread.getCategory().getId(), "name", thread.getCategory().getName()));
        result.put("comments",     commentList);
        return result;
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    @Transactional
    public ForumComment addComment(UUID threadId, UUID userId, String body) {
        ForumThread thread = threadRepo.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
        if (thread.getIsLocked()) throw new IllegalStateException("This thread is locked");
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (body == null || body.isBlank()) throw new IllegalArgumentException("Comment body is required");

        ForumComment comment = ForumComment.builder()
                .thread(thread)
                .body(body.trim())
                .createdBy(user)
                .build();
        commentRepo.save(comment);

        // Update thread counters
        thread.setCommentCount(thread.getCommentCount() + 1);
        thread.setLastActivityAt(LocalDateTime.now());
        threadRepo.save(thread);

        return comment;
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getPanel() {
        List<ForumThread> mostActive = threadRepo.findMostActiveThreads(PageRequest.of(0, 1));
        List<ForumThread> recentlyCreated = threadRepo.findRecentlyCreatedThreads(PageRequest.of(0, 1));
        List<ForumThread> recentActivity = threadRepo.findRecentActivityThreads(PageRequest.of(0, 1));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mostActiveThread",     mostActive.isEmpty()     ? null : toThreadRef(mostActive.get(0)));
        result.put("recentlyCreatedThread", recentlyCreated.isEmpty() ? null : toThreadRef(recentlyCreated.get(0)));
        result.put("recentActivityThread", recentActivity.isEmpty()  ? null : toThreadRef(recentActivity.get(0)));
        return result;
    }

    // ── Admin: pin / lock ─────────────────────────────────────────────────────

    @Transactional
    public void pinThread(UUID threadId, boolean pin) {
        ForumThread t = threadRepo.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
        t.setIsPinned(pin);
        threadRepo.save(t);
    }

    @Transactional
    public void lockThread(UUID threadId, boolean lock) {
        ForumThread t = threadRepo.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
        t.setIsLocked(lock);
        threadRepo.save(t);
    }

    @Transactional
    public void editThreadBody(UUID threadId, UUID actorId, String newBody) {
        ForumThread t = threadRepo.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));
        if (!t.getCreatedBy().getId().equals(actorId))
            throw new IllegalStateException("You can only edit your own thread");
        if (newBody == null || newBody.isBlank())
            throw new IllegalArgumentException("Body cannot be empty");
        t.setBody(newBody.trim());
        threadRepo.save(t);
    }

    @Transactional
    public void deleteThread(UUID threadId) {
        threadRepo.deleteById(threadId);
    }

    @Transactional
    public void editComment(UUID commentId, UUID actorId, String newBody) {
        ForumComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        if (!c.getCreatedBy().getId().equals(actorId))
            throw new IllegalStateException("You can only edit your own comment");
        if (newBody == null || newBody.isBlank())
            throw new IllegalArgumentException("Body cannot be empty");
        c.setBody(newBody.trim());
        commentRepo.save(c);
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID actorId, boolean isAdmin) {
        ForumComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        if (!isAdmin && !c.getCreatedBy().getId().equals(actorId))
            throw new IllegalStateException("You can only delete your own comment");
        // Decrement thread comment count
        ForumThread thread = c.getThread();
        thread.setCommentCount(Math.max(0, thread.getCommentCount() - 1));
        threadRepo.save(thread);
        commentRepo.delete(c);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildThreadSummary(ForumThread t, User viewer, LocalDateTime lastReadAt) {
        long unread;
        if (lastReadAt == null) {
            unread = t.getCommentCount();
        } else {
            unread = commentRepo.countByThreadIdAndCreatedAtAfter(t.getId(), lastReadAt);
        }

        boolean hasPosted = commentRepo.existsByThreadIdAndCreatedBy(t.getId(), viewer)
                || t.getCreatedBy().getId().equals(viewer.getId());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",              t.getId());
        m.put("title",           t.getTitle());
        m.put("isPinned",        t.getIsPinned());
        m.put("isLocked",        t.getIsLocked());
        m.put("commentCount",    t.getCommentCount());
        m.put("unreadCount",     unread);
        m.put("hasPosted",       hasPosted);   // true = red star, false = yellow star
        m.put("createdAt",       t.getCreatedAt());
        m.put("lastActivityAt",  t.getLastActivityAt());
        m.put("authorName",      t.getCreatedBy().getName());
        m.put("authorUsername",  t.getCreatedBy().getUsername());
        m.put("authorProfilePicUrl", t.getCreatedBy().getProfilePicUrl());
        return m;
    }

    private Map<String, Object> toThreadRef(ForumThread t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           t.getId());
        m.put("title",        t.getTitle());
        m.put("commentCount", t.getCommentCount());
        m.put("createdAt",    t.getCreatedAt());
        m.put("lastActivityAt", t.getLastActivityAt());
        m.put("authorName",   t.getCreatedBy().getName());
        m.put("categoryId",   t.getCategory().getId());
        m.put("categoryName", t.getCategory().getName());
        return m;
    }
}
