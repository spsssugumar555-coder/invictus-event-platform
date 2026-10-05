package com.invictus.platform.repository;

import com.invictus.platform.entity.Round2Draft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface Round2DraftRepository extends JpaRepository<Round2Draft, Long> {
    Optional<Round2Draft> findByTeamId(Long teamId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Round2Draft d where d.team.id = :teamId")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
