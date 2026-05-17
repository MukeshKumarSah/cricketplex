package com.cricketplex.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class BlogReactionId implements Serializable {

    private UUID blogId;
    private UUID userId;
    private String reaction;

    public BlogReactionId() {}

    public BlogReactionId(UUID blogId, UUID userId, String reaction) {
        this.blogId = blogId;
        this.userId = userId;
        this.reaction = reaction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlogReactionId that)) return false;
        return Objects.equals(blogId, that.blogId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(reaction, that.reaction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blogId, userId, reaction);
    }
}
