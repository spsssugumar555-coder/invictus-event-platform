package com.invictus.platform.repository;

import com.invictus.platform.entity.Round2Evaluation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface Round2EvaluationRepository extends JpaRepository<Round2Evaluation, Long> {
    Optional<Round2Evaluation> findBySubmissionId(Long submissionId);
    long countByStatus(String status);

    @Modifying(flushAutomatically = true)
    @Query("delete from Round2Evaluation e where e.submission.id in (select s.id from Round2Submission s where s.team.id = :teamId)")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
