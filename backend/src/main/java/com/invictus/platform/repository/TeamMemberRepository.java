package com.invictus.platform.repository;

import com.invictus.platform.entity.TeamMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByTeamId(Long teamId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TeamMember m where m.team.id = :teamId")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
