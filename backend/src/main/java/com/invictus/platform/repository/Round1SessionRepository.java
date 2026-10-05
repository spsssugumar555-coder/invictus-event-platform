package com.invictus.platform.repository;

import com.invictus.platform.entity.Round1Session;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Round1SessionRepository extends JpaRepository<Round1Session, Long> {
    Optional<Round1Session> findByTeamId(Long teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Round1Session s where s.team.id = :teamId")
    Optional<Round1Session> lockByTeamId(@Param("teamId") Long teamId);

    long countByStatus(String status);

    @Modifying(flushAutomatically = true)
    @Query("delete from Round1Session s where s.team.id = :teamId")
    void deleteAllForTeam(@Param("teamId") Long teamId);
}
