package com.invictus.platform.repository;

import com.invictus.platform.entity.Round2Submission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface Round2SubmissionRepository extends JpaRepository<Round2Submission, Long> {
    Optional<Round2Submission> findByTeamId(Long teamId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Round2Submission s where s.team.id = :teamId")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
