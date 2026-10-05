package com.invictus.platform.repository;

import com.invictus.platform.entity.TeamSequenceItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamSequenceItemRepository extends JpaRepository<TeamSequenceItem, Long> {
    List<TeamSequenceItem> findByTeamIdOrderBySortPosAsc(Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamSequenceItem t where t.team.id = :teamId")
    void deleteAllForTeam(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamSequenceItem t where t.question.id = :questionId")
    void deleteAllForQuestion(@Param("questionId") Long questionId);
}
