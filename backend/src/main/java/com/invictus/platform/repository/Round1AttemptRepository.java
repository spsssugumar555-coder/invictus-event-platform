package com.invictus.platform.repository;

import com.invictus.platform.entity.Round1Attempt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface Round1AttemptRepository extends JpaRepository<Round1Attempt, Long> {
    Optional<Round1Attempt> findBySessionIdAndStatus(Long sessionId, String status);
    Optional<Round1Attempt> findByIdAndSessionId(Long id, Long sessionId);
    long countBySessionIdAndStatusNot(Long sessionId, String status);
    List<Round1Attempt> findBySessionId(Long sessionId);
    boolean existsByQuestionId(Long questionId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Round1Attempt a where a.session.id in (select s.id from Round1Session s where s.team.id = :teamId)")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
