package com.invictus.platform.repository;

import com.invictus.platform.entity.Round2Challenge;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Round2ChallengeRepository extends JpaRepository<Round2Challenge, Long> {
    Optional<Round2Challenge> findFirstByActiveTrueOrderByIdAsc();
}
