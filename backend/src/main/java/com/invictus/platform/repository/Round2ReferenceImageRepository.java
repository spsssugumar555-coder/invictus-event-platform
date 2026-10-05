package com.invictus.platform.repository;

import com.invictus.platform.entity.Round2ReferenceImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Round2ReferenceImageRepository extends JpaRepository<Round2ReferenceImage, Long> {
    Optional<Round2ReferenceImage> findByChallengeId(Long challengeId);
}
