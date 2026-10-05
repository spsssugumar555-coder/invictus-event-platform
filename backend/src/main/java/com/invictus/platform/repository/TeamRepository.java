package com.invictus.platform.repository;

import com.invictus.platform.entity.Team;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByUserId(Long userId);
    boolean existsByNameIgnoreCase(String name);
}
