package com.invictus.platform.service;

import com.invictus.platform.ai.AiEvaluationService;
import com.invictus.platform.dto.AdminDtos.*;
import com.invictus.platform.entity.Qualification;
import com.invictus.platform.entity.Round1Attempt;
import com.invictus.platform.entity.Round1Session;
import com.invictus.platform.entity.Round2Evaluation;
import com.invictus.platform.entity.Round2Submission;
import com.invictus.platform.entity.Team;
import com.invictus.platform.entity.TeamMember;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.QualificationRepository;
import com.invictus.platform.repository.QuestionRepository;
import com.invictus.platform.repository.Round1AttemptRepository;
import com.invictus.platform.repository.Round1SessionRepository;
import com.invictus.platform.repository.Round2EvaluationRepository;
import com.invictus.platform.repository.Round2SubmissionRepository;
import com.invictus.platform.repository.TeamMemberRepository;
import com.invictus.platform.repository.TeamRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final Round1SessionRepository sessions;
    private final Round1AttemptRepository attempts;
    private final QuestionRepository questions;
    private final QualificationRepository qualifications;
    private final Round2SubmissionRepository submissions;
    private final Round2EvaluationRepository evaluations;
    private final RankingService ranking;
    private final QualificationService qualificationService;
    private final AiEvaluationService aiEvaluation;

    public AdminService(TeamRepository teams, TeamMemberRepository members, Round1SessionRepository sessions,
                        Round1AttemptRepository attempts, QuestionRepository questions,
                        QualificationRepository qualifications, Round2SubmissionRepository submissions,
                        Round2EvaluationRepository evaluations, RankingService ranking,
                        QualificationService qualificationService, AiEvaluationService aiEvaluation) {
        this.teams = teams;
        this.members = members;
        this.sessions = sessions;
        this.attempts = attempts;
        this.questions = questions;
        this.qualifications = qualifications;
        this.submissions = submissions;
        this.evaluations = evaluations;
        this.ranking = ranking;
        this.qualificationService = qualificationService;
        this.aiEvaluation = aiEvaluation;
    }

    @Transactional(readOnly = true)
    public DashboardStats dashboard() {
        long teamCount = teams.count();
        return new DashboardStats(teamCount, teamCount + members.count(),
                sessions.countByStatus("IN_PROGRESS"), sessions.countByStatus("COMPLETED"),
                qualifications.countByStatus("QUALIFIED"), submissions.count(),
                evaluations.countByStatus("COMPLETED"), qualifications.count() > 0);
    }

    @Transactional(readOnly = true)
    public List<TeamRow> teams() {
        Map<Long, List<String>> membersByTeam = new HashMap<>();
        for (TeamMember m : members.findAll()) {
            membersByTeam.computeIfAbsent(m.team.id, k -> new ArrayList<>()).add(m.name);
        }
        Map<Long, Round1Session> sessionByTeam = new HashMap<>();
        sessions.findAll().forEach(s -> sessionByTeam.put(s.team.id, s));
        Map<Long, Qualification> qualByTeam = new HashMap<>();
        qualifications.findAll().forEach(q -> qualByTeam.put(q.team.id, q));
        Map<Long, Integer> ranks = ranking.rankByTeam();
        Map<Long, Round2Submission> subByTeam = new HashMap<>();
        submissions.findAll().forEach(x -> subByTeam.put(x.team.id, x));
        Map<Long, Round2Evaluation> evalBySub = new HashMap<>();
        evaluations.findAll().forEach(e -> evalBySub.put(e.submission.id, e));

        List<TeamRow> rows = new ArrayList<>();
        for (Team t : teams.findAll()) {
            Round1Session s = sessionByTeam.get(t.id);
            Qualification q = qualByTeam.get(t.id);
            String qualification = q == null ? "PENDING" : q.status;
            String round = "QUALIFIED".equals(qualification) ? "ROUND_2"
                    : "NOT_QUALIFIED".equals(qualification) ? "ENDED" : "ROUND_1";
            String round1Status = s == null ? "NOT_STARTED" : s.status;
            Round2Submission sub = subByTeam.get(t.id);
            Round2Evaluation ev = sub == null ? null : evalBySub.get(sub.id);
            String round2Status = !"QUALIFIED".equals(qualification) ? "LOCKED"
                    : sub == null ? "NOT_SUBMITTED"
                    : ev != null && "COMPLETED".equals(ev.status) ? "EVALUATED" : "SUBMITTED";
            rows.add(new TeamRow(t.id, t.name, t.captainName, t.captainEmail,
                    membersByTeam.getOrDefault(t.id, List.of()), round,
                    s == null ? null : s.score, ranks.get(t.id), qualification, round1Status, round2Status));
        }
        rows.sort(Comparator.comparing(r -> r.name().toLowerCase()));
        return rows;
    }

    @Transactional(readOnly = true)
    public TeamRow team(Long id) {
        return teams().stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
    }

    @Transactional(readOnly = true)
    public List<Round1Row> round1() {
        int total = (int) questions.count();
        List<Round1Row> rows = new ArrayList<>();
        int rank = 1;
        for (Round1Session s : ranking.ordered()) {
            Integer current = attempts.findBySessionIdAndStatus(s.id, "ACTIVE")
                    .map((Round1Attempt a) -> a.question.number).orElse(null);
            rows.add(new Round1Row(s.team.id, s.team.name, s.status, s.correctCount, total, current,
                    s.correctCount, s.incorrectCount, s.timeoutCount, s.score, rank++));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<RankingRow> rankings() {
        List<RankingRow> rows = new ArrayList<>();
        int rank = 1;
        for (Round1Session s : ranking.ordered()) {
            rows.add(new RankingRow(rank++, s.team.id, s.team.name, s.score, s.correctCount, s.correctCount, s.status));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<QualificationRow> qualifications() {
        return qualifications.findAll().stream()
                .sorted(Comparator.comparing((Qualification q) -> q.rankNo == null ? Integer.MAX_VALUE : q.rankNo))
                .map(q -> new QualificationRow(q.team.id, q.team.name, q.status, q.rankNo, q.score))
                .toList();
    }

    public List<QualificationRow> finalizeRound1() {
        qualificationService.finalizeRound1();
        return qualifications();
    }

    @Transactional(readOnly = true)
    public List<SubmissionRow> submissions() {
        Map<Long, Round2Evaluation> evalBySub = new HashMap<>();
        evaluations.findAll().forEach(e -> evalBySub.put(e.submission.id, e));
        return submissions.findAll().stream()
                .sorted(Comparator.comparing((Round2Submission s) -> s.submittedAt))
                .map(s -> {
                    Round2Evaluation e = evalBySub.get(s.id);
                    return new SubmissionRow(s.id, s.team.id, s.team.name, s.submittedAt,
                            e == null ? "NONE" : e.status, e == null ? null : e.finalScore);
                }).toList();
    }

    /** Round 2 results, best first (ADMIN only). */
    @Transactional(readOnly = true)
    public List<ResultRow> results() {
        Map<Long, Round2Evaluation> evalBySub = new HashMap<>();
        evaluations.findAll().forEach(e -> evalBySub.put(e.submission.id, e));
        List<Round2Submission> subs = new ArrayList<>(submissions.findAll());
        Comparator<Round2Submission> byScore = Comparator.comparing((Round2Submission x) -> {
            Round2Evaluation e = evalBySub.get(x.id);
            return e == null || e.finalScore == null ? -1.0 : e.finalScore;
        }).reversed().thenComparing(x -> x.submittedAt);
        subs.sort(byScore);
        List<ResultRow> rows = new ArrayList<>();
        int rank = 1;
        for (Round2Submission x : subs) {
            Round2Evaluation e = evalBySub.get(x.id);
            rows.add(new ResultRow(rank++, x.id, x.team.id, x.team.name, x.submittedAt,
                    e == null ? "NONE" : e.status, e == null ? null : e.finalScore));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public SubmissionDetail submission(Long id) {
        Round2Submission s = submissions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Submission not found"));
        return detail(s, evaluations.findBySubmissionId(id).orElse(null));
    }

    /** Runs (or re-runs) the AI evaluation. Not transactional: see AiEvaluationService. */
    public SubmissionDetail evaluate(Long submissionId) {
        Round2Evaluation e = aiEvaluation.evaluate(submissionId);
        return detail(e.submission, e);
    }

    private static SubmissionDetail detail(Round2Submission s, Round2Evaluation e) {
        EvaluationDto dto = e == null ? null : new EvaluationDto(e.status, e.requirementScore, e.functionalityScore,
                e.uiScore, e.responsivenessScore, e.technicalScore, e.accuracyScore, e.finalScore, e.feedback,
                e.errorMessage, e.rawResponse, e.evaluatedAt);
        return new SubmissionDetail(s.id, s.team.id, s.team.name, s.generatedHtml, s.description,
                s.submittedAt, dto);
    }
}
