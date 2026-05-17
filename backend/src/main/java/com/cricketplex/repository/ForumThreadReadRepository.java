package com.cricketplex.repository;

import com.cricketplex.entity.ForumThreadRead;
import com.cricketplex.entity.ForumThreadReadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumThreadReadRepository extends JpaRepository<ForumThreadRead, ForumThreadReadId> {

    Optional<ForumThreadRead> findByIdUserIdAndIdThreadId(UUID userId, UUID threadId);

    @Query("SELECT r FROM ForumThreadRead r WHERE r.id.userId = :userId AND r.id.threadId IN :threadIds")
    List<ForumThreadRead> findByUserIdAndThreadIdIn(@Param("userId") UUID userId,
                                                     @Param("threadIds") List<UUID> threadIds);
}
