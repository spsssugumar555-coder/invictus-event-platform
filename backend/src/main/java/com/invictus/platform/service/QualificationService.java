package com.invictus.platform.service;

import com.invictus.platform.entity.Qualification;
import com.invictus.platform.entity.Round1Session;
import com.invictus.platform.entity.Team;
import com.invictus.platform.repository.QualificationRepository;
import com.invictus.platform.repository.TeamRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualificationService {
    public static final int TOP_N = 10;

    private final RankingService ranking;
    private final QualificationRepository qualifications;
    private final TeamRepository teams;

    public QualificationService(RankingService ranking, QualificationRepository qualifications, TeamRepository teams) {
        this.ranking = ranking;
        this.qualifications = qualifications;
        this.teams = teams;
    }

    /**
     * Ends Round 1. Only teams that COMPLETED Round 1 (every question answered correctly) are eligible; they are
     * ranked with the existing RankingService order and ranks 1-10 become QUALIFIED. Everyone else is NOT_QUALIFIED.
     */
    @Transactional
    public List<Qualification> finalizeRound1() {
        Set<Long> ranked = new HashSet<>();
        int rank = 1;
        Instant now = Instant.now();
        for (Round1Session s : ranking.ordered()) {
            boolean completed = "COMPLETED".equals(s.status);
            Qualification q = qualifications.findByTeamId(s.team.id).orElseGet(Qualification::new);
            q.team = s.team;
            q.score = s.score;
            q.rankNo = completed ? rank : null;
            q.status = completed && rank <= TOP_N ? "QUALIFIED" : "NOT_QUALIFIED";
            q.decidedAt = now;
            qualifications.save(q);
            ranked.add(s.team.id);
            if (completed) {
                rank++;
            }
        }
        for (Team t : teams.findAll()) {
            if (!ranked.contains(t.id)) {
                Qualification q = qualifications.findByTeamId(t.id).orElseGet(Qualification::new);
                q.team = t;
                q.rankNo = null;
                q.score = 0;
                q.status = "NOT_QUALIFIED";
                q.decidedAt = now;
                qualifications.save(q);
            }
        }
        return qualifications.findAll();
    }
}
