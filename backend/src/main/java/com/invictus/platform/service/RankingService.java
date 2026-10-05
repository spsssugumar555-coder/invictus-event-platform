package com.invictus.platform.service;

import com.invictus.platform.entity.Round1Session;
import com.invictus.platform.repository.Round1SessionRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranks every team that has started Round 1. Order: score (high first), then correct answers
 * (high first), then earlier completion, then earlier registration.
 */
@Service
public class RankingService {
    private static final Instant NEVER = Instant.MAX;

    private final Round1SessionRepository sessions;

    public RankingService(Round1SessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<Round1Session> ordered() {
        return sessions.findAll().stream()
                .sorted(Comparator
                        .comparingInt((Round1Session s) -> s.score).reversed()
                        .thenComparing(Comparator.comparingInt((Round1Session s) -> s.correctCount).reversed())
                        .thenComparing(s -> s.completedAt == null ? NEVER : s.completedAt)
                        .thenComparing(s -> s.team.id))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> rankByTeam() {
        Map<Long, Integer> ranks = new HashMap<>();
        int rank = 1;
        for (Round1Session s : ordered()) {
            ranks.put(s.team.id, rank++);
        }
        return ranks;
    }
}
