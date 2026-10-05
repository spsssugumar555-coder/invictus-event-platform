package com.invictus.platform.repository;

import com.invictus.platform.entity.Qualification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface QualificationRepository extends JpaRepository<Qualification, Long> {
    Optional<Qualification> findByTeamId(Long teamId);
    long countByStatus(String status);

    @Modifying(flushAutomatically = true)
    @Query("delete from Qualification q where q.team.id = :teamId")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
