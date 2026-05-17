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
public class BlogService {

    private static final int PAGE_SIZE = 10;
    private static final List<String> ALLOWED_REACTIONS =
            List.of("❤️", "👍", "👎", "🔥", "💯", "😂", "🎉", "👀", "🙏");

    private final BlogRepository blogRepo;
    private final BlogReactionRepository reactionRepo;
    private final UserRepository userRepo;
    private final ForumCategoryRepository categoryRepo;
    private final ForumThreadRepository threadRepo;

    // ── List ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> listBlogs(int page, UUID viewerId) {
        Page<Blog> blogPage = blogRepo.findAllOrderByPinnedDesc(PageRequest.of(page, PAGE_SIZE));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Blog b : blogPage.getContent()) {
            items.add(toBlogSummary(b, viewerId));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blogs",      items);
        result.put("page",       page);
        result.put("totalPages", blogPage.getTotalPages());
        result.put("total",      blogPage.getTotalElements());
        return result;
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getBlog(UUID blogId, UUID viewerId) {
        Blog blog = blogRepo.findById(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",            blog.getId());
        m.put("title",         blog.getTitle());
        m.put("description",   blog.getDescription());
        m.put("body",          blog.getBody());
        m.put("authorId",      blog.getAuthor().getId());
        m.put("authorName",    blog.getAuthorName());
        m.put("isPinned",      blog.getIsPinned());
        m.put("createdAt",     blog.getCreatedAt());
        m.put("updatedAt",     blog.getUpdatedAt());
        if (blog.getForumThread() != null) {
            m.put("forumThreadId", blog.getForumThread().getId());
        }

        // Reactions grouped by emoji with current user's set
        List<BlogReaction> reactions = reactionRepo.findByBlogId(blogId);
        Map<String, Long> counts = new LinkedHashMap<>();
        Set<String> myReactions = new HashSet<>();
        for (BlogReaction r : reactions) {
            counts.merge(r.getReaction(), 1L, Long::sum);
            if (viewerId != null && r.getUserId().equals(viewerId)) {
                myReactions.add(r.getReaction());
            }
        }
        m.put("reactions",   counts);
        m.put("myReactions", new ArrayList<>(myReactions));
        return m;
    }

    // ── New count ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long newBlogCount(LocalDateTime since) {
        return blogRepo.countByCreatedAtAfter(since);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> createBlog(UUID authorId, boolean isAdmin, String title, String description, String body) {
        User author = userRepo.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Title is required");
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Description is required");

        // 24-hour cooldown for non-admins
        if (!isAdmin) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            if (blogRepo.existsByAuthorIdAndCreatedAtAfter(authorId, cutoff)) {
                throw new IllegalStateException("You can only publish one blog every 24 hours. Please try again later.");
            }
        }

        Blog blog = Blog.builder()
                .title(title.trim())
                .description(description.trim())
                .body(body != null ? body.trim() : "")
                .author(author)
                .authorName(author.getName())
                .build();
        blog = blogRepo.save(blog);

        // Auto-create forum thread in "Blog" category
        try {
            ForumCategory blogCat = categoryRepo.findByName("Blog")
                    .orElse(null);
            if (blogCat != null) {
                UUID blogId = blog.getId();
                String threadBody = description.trim()
                        + "\n\n[Read the full blog here →](/blog/" + blogId + ")";
                ForumThread thread = ForumThread.builder()
                        .category(blogCat)
                        .title(title.trim())
                        .body(threadBody)
                        .createdBy(author)
                        .isLocked(true)
                        .build();
                thread = threadRepo.save(thread);
                blog.setForumThread(thread);
                blog = blogRepo.save(blog);
            }
        } catch (Exception ignored) {
            // Forum thread creation is best-effort — don't fail the blog creation
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id",    blog.getId());
        result.put("title", blog.getTitle());
        if (blog.getForumThread() != null) {
            result.put("forumThreadId", blog.getForumThread().getId());
        }
        return result;
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    @Transactional
    public void editBlog(UUID blogId, UUID editorId, boolean isAdmin,
                         String title, String description, String body) {
        Blog blog = blogRepo.findById(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found"));

        if (!isAdmin && !blog.getAuthor().getId().equals(editorId)) {
            throw new IllegalStateException("Not authorized to edit this blog");
        }
        if (title != null && !title.isBlank())       blog.setTitle(title.trim());
        if (description != null && !description.isBlank()) blog.setDescription(description.trim());
        if (body != null)                              blog.setBody(body.trim());
        blogRepo.save(blog);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteBlog(UUID blogId, UUID deleterId, boolean isAdmin) {
        Blog blog = blogRepo.findById(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found"));

        if (!isAdmin && !blog.getAuthor().getId().equals(deleterId)) {
            throw new IllegalStateException("Not authorized to delete this blog");
        }
        blogRepo.delete(blog);
    }

    // ── Pin ───────────────────────────────────────────────────────────────────

    @Transactional
    public void pinBlog(UUID blogId, boolean pin) {
        Blog blog = blogRepo.findById(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found"));
        blog.setIsPinned(pin);
        blogRepo.save(blog);
    }

    // ── React ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> toggleReaction(UUID blogId, UUID userId, String reaction) {
        if (!blogRepo.existsById(blogId))
            throw new IllegalArgumentException("Blog not found");
        if (!ALLOWED_REACTIONS.contains(reaction))
            throw new IllegalArgumentException("Invalid reaction");

        boolean existed = reactionRepo.existsByBlogIdAndUserIdAndReaction(blogId, userId, reaction);
        if (existed) {
            reactionRepo.deleteByBlogIdAndUserIdAndReaction(blogId, userId, reaction);
        } else {
            BlogReaction r = BlogReaction.builder()
                    .blogId(blogId)
                    .userId(userId)
                    .reaction(reaction)
                    .build();
            reactionRepo.save(r);
        }

        // Return updated reaction counts + caller's reactions
        List<BlogReaction> all = reactionRepo.findByBlogId(blogId);
        Map<String, Long> counts = new LinkedHashMap<>();
        Set<String> myReactions = new HashSet<>();
        for (BlogReaction r : all) {
            counts.merge(r.getReaction(), 1L, Long::sum);
            if (r.getUserId().equals(userId)) myReactions.add(r.getReaction());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reactions",   counts);
        result.put("myReactions", new ArrayList<>(myReactions));
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> toBlogSummary(Blog b, UUID viewerId) {
        List<BlogReaction> reactions = reactionRepo.findByBlogId(b.getId());
        Map<String, Long> counts = new LinkedHashMap<>();
        Set<String> myReactions = new HashSet<>();
        for (BlogReaction r : reactions) {
            counts.merge(r.getReaction(), 1L, Long::sum);
            if (viewerId != null && r.getUserId().equals(viewerId)) {
                myReactions.add(r.getReaction());
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          b.getId());
        m.put("title",       b.getTitle());
        m.put("description", b.getDescription());
        m.put("authorId",    b.getAuthor().getId());
        m.put("authorName",  b.getAuthorName());
        m.put("isPinned",    b.getIsPinned());
        m.put("createdAt",   b.getCreatedAt());
        m.put("reactions",   counts);
        m.put("myReactions", new ArrayList<>(myReactions));
        if (b.getForumThread() != null) {
            m.put("forumThreadId", b.getForumThread().getId());
        }
        return m;
    }
}
