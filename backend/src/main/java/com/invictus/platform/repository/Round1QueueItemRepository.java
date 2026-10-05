package com.invictus.platform.repository;

import com.invictus.platform.entity.Round1QueueItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface Round1QueueItemRepository extends JpaRepository<Round1QueueItem, Long> {
    List<Round1QueueItem> findBySessionId(Long sessionId);
    boolean existsBySessionId(Long sessionId);
    boolean existsByQuestionId(Long questionId);
    Optional<Round1QueueItem> findBySessionIdAndQuestionId(Long sessionId, Long questionId);
    Optional<Round1QueueItem> findFirstBySessionIdAndCompletedFalseOrderBySortPosAsc(Long sessionId);
    Optional<Round1QueueItem> findFirstBySessionIdAndCompletedFalseOrderBySortPosDesc(Long sessionId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Round1QueueItem i where i.session.id in (select s.id from Round1Session s where s.team.id = :teamId)")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
