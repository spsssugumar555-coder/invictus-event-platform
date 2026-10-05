package com.invictus.platform.service;

import com.invictus.platform.dto.Round1Dtos.CrosswordView;
import com.invictus.platform.dto.Round1Dtos.QuestionView;
import com.invictus.platform.dto.Round1Dtos.Round1State;
import com.invictus.platform.dto.Round1Dtos.Round1Summary;
import com.invictus.platform.entity.Question;
import com.invictus.platform.entity.Round1Attempt;
import com.invictus.platform.entity.Round1QueueItem;
import com.invictus.platform.entity.Round1Session;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.QualificationRepository;
import com.invictus.platform.repository.QuestionRepository;
import com.invictus.platform.repository.Round1AttemptRepository;
import com.invictus.platform.repository.Round1ConfigRepository;
import com.invictus.platform.repository.Round1QueueItemRepository;
import com.invictus.platform.repository.Round1SessionRepository;
import com.invictus.platform.repository.TeamRepository;
import com.invictus.platform.repository.TeamSequenceItemRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Round 1 engine.
 *
 * Each team has a queue of questions. The head of the queue is the active question and gets a server-side
 * 120 second deadline.
 *   correct   -> the question is completed and leaves the queue for good
 *   incorrect -> nothing moves: the same question stays active on the same deadline and can be retried
 *   timeout   -> the question goes to the END of the queue and the next question starts
 * There is no skip. The round is COMPLETED only when every question has been answered correctly.
 *
 * Every state-changing call locks the team's session row, so double clicks and parallel tabs cannot
 * corrupt the queue or the counters.
 */
@Service
public class Round1Service {
    public static final int POINTS_PER_CORRECT = 10;
    public static final long QUESTION_SECONDS = 120;

    private final Round1SessionRepository sessions;
    private final Round1AttemptRepository attempts;
    private final Round1QueueItemRepository queue;
    private final QuestionRepository questions;
    private final TeamSequenceItemRepository sequences;
    private final Round1ConfigRepository config;
    private final TeamRepository teams;
    private final QualificationRepository qualifications;
    private final CrosswordService crosswords;

    public Round1Service(Round1SessionRepository sessions, Round1AttemptRepository attempts,
                         Round1QueueItemRepository queue, QuestionRepository questions,
                         TeamSequenceItemRepository sequences, Round1ConfigRepository config,
                         TeamRepository teams, QualificationRepository qualifications,
                         CrosswordService crosswords) {
        this.sessions = sessions;
        this.attempts = attempts;
        this.queue = queue;
        this.questions = questions;
        this.sequences = sequences;
        this.config = config;
        this.teams = teams;
        this.qualifications = qualifications;
        this.crosswords = crosswords;
    }

    public boolean isClosed() {
        return qualifications.count() > 0;
    }

    public boolean isPublished() {
        return config.findById(1L).map(c -> c.published).orElse(false);
    }

    @Transactional(readOnly = true)
    public Round1Summary summary(Long teamId) {
        return sessions.findByTeamId(teamId).map(this::summaryOf).orElseGet(() -> {
            String status = !isPublished() ? "NOT_PUBLISHED" : isClosed() ? "CLOSED" : "NOT_STARTED";
            return new Round1Summary(status, 0, (int) questions.count(), 0, 0, 0, 0);
        });
    }

    /** The team's current question. The first call starts the session; expired questions are settled here. */
    @Transactional
    public Round1State current(Long teamId) {
        if (!isPublished() || isClosed()) {
            return new Round1State(summary(teamId), null, null, null);
        }
        Round1Session s = lockOrCreate(teamId);
        String last = null;
        Round1Attempt active = attempts.findBySessionIdAndStatus(s.id, "ACTIVE").orElse(null);
        if (active != null && expired(active)) {
            timeOut(s, active);
            last = "TIMEOUT";
        }
        ensureActive(s);
        return state(s, last);
    }

    @Transactional
    public Round1State answer(Long teamId, Long attemptId, String rawAnswer) {
        requireOpen();
        Round1Session s = lockExisting(teamId);
        // An attempt id that is not in THIS team's session simply does not exist for the caller.
        Round1Attempt a = attempts.findByIdAndSessionId(attemptId, s.id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Question not found"));
        // Old, timed-out, or already answered attempts must not change any state.
        if (!"ACTIVE".equals(a.status)) {
            throw new ApiException(HttpStatus.CONFLICT, "That question is no longer active");
        }
        if (expired(a)) {
            timeOut(s, a);
            ensureActive(s);
            return state(s, "TIMEOUT");
        }

        a.submittedAnswer = rawAnswer.length() > 100 ? rawAnswer.substring(0, 100) : rawAnswer;
        if (CrosswordGenerator.normalize(rawAnswer).equals(CrosswordGenerator.normalize(a.question.answer))) {
            Round1QueueItem item = queue.findBySessionIdAndQuestionId(s.id, a.question.id)
                    .orElseThrow(() -> new IllegalStateException("Queue item missing for question " + a.question.id));
            a.status = "CORRECT";
            a.closedAt = Instant.now();
            s.correctCount++;
            s.score += POINTS_PER_CORRECT;
            item.completed = true;
            ensureActive(s);
            return state(s, "CORRECT");
        }
        // Wrong answer: the attempt stays ACTIVE with its original expiresAt, the queue is untouched,
        // and the player may try again until the server deadline passes.
        s.incorrectCount++;
        return state(s, "INCORRECT");
    }

    // ---------------------------------------------------------------- internals

    private void requireOpen() {
        if (!isPublished()) {
            throw new ApiException(HttpStatus.CONFLICT, "Round 1 has not started");
        }
        if (isClosed()) {
            throw new ApiException(HttpStatus.CONFLICT, "Round 1 has ended");
        }
    }

    private boolean expired(Round1Attempt a) {
        return !Instant.now().isBefore(a.expiresAt);
    }

    private Round1Session lockExisting(Long teamId) {
        Round1Session s = sessions.lockByTeamId(teamId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Round 1 has not been started"));
        ensureQueue(s);
        return s;
    }

    private Round1Session lockOrCreate(Long teamId) {
        Round1Session s = sessions.lockByTeamId(teamId).orElseGet(() -> {
            if (questions.count() == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Round 1 has no questions");
            }
            Round1Session n = new Round1Session();
            n.team = teams.findById(teamId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
            n.status = "IN_PROGRESS";
            n.startedAt = Instant.now();
            return sessions.saveAndFlush(n);
        });
        ensureQueue(s);
        return s;
    }

    /**
     * Builds the team's queue once: admin-configured sequence first, then every remaining question in a
     * deterministic team-specific order. Sessions created before the queue existed are migrated here
     * (questions they already answered correctly are marked completed).
     */
    private void ensureQueue(Round1Session s) {
        if (queue.existsBySessionId(s.id)) {
            return;
        }
        List<Question> ordered = questions.findAllByOrderByNumberAsc();
        int n = ordered.size();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        sequences.findByTeamIdOrderBySortPosAsc(s.team.id).forEach(i -> ids.add(i.question.id));
        for (int i = 0; i < n; i++) {
            ids.add(ordered.get(QuestionOrder.numberAt(s.team.id, i, n) - 1).id);
        }
        Set<Long> alreadyCorrect = new HashSet<>();
        for (Round1Attempt a : attempts.findBySessionId(s.id)) {
            if ("CORRECT".equals(a.status)) {
                alreadyCorrect.add(a.question.id);
            }
        }
        int pos = 1;
        for (Long id : ids) {
            Round1QueueItem item = new Round1QueueItem();
            item.session = s;
            item.question = ordered.stream().filter(q -> q.id.equals(id)).findFirst().orElseThrow();
            item.sortPos = pos++;
            item.completed = alreadyCorrect.contains(id);
            queue.save(item);
        }
    }

    private void moveToEnd(Round1Session s, Round1QueueItem item) {
        int max = queue.findFirstBySessionIdAndCompletedFalseOrderBySortPosDesc(s.id).map(i -> i.sortPos).orElse(0);
        item.sortPos = max + 1;
    }

    private void timeOut(Round1Session s, Round1Attempt a) {
        a.status = "TIMEOUT";
        a.closedAt = Instant.now();
        s.timeoutCount++;
        queue.findBySessionIdAndQuestionId(s.id, a.question.id).ifPresent(item -> moveToEnd(s, item));
    }

    /** Make sure the head of the queue has an active attempt, or complete the session when nothing is left. */
    private void ensureActive(Round1Session s) {
        if ("COMPLETED".equals(s.status)) {
            return;
        }
        if (attempts.findBySessionIdAndStatus(s.id, "ACTIVE").isPresent()) {
            return;
        }
        Round1QueueItem head = queue.findFirstBySessionIdAndCompletedFalseOrderBySortPosAsc(s.id).orElse(null);
        if (head == null) {
            s.status = "COMPLETED";
            s.completedAt = Instant.now();
            return;
        }
        Round1Attempt a = new Round1Attempt();
        a.session = s;
        a.question = head.question;
        a.status = "ACTIVE";
        a.startedAt = Instant.now();
        a.expiresAt = a.startedAt.plus(Duration.ofSeconds(QUESTION_SECONDS));
        attempts.save(a);
    }

    private Round1State state(Round1Session s, String lastResult) {
        Round1Attempt active = attempts.findBySessionIdAndStatus(s.id, "ACTIVE").orElse(null);
        QuestionView view = null;
        if (active != null) {
            Question q = active.question;
            long remaining = Math.max(0, Duration.between(Instant.now(), active.expiresAt).toMillis());
            view = new QuestionView(active.id, q.displayNumber == null ? 0 : q.displayNumber, q.clue, q.direction,
                    CrosswordGenerator.normalize(q.answer).length(),
                    q.gridRow == null ? 0 : q.gridRow, q.gridCol == null ? 0 : q.gridCol,
                    remaining, QUESTION_SECONDS * 1000);
        }
        Set<Long> solved = new HashSet<>();
        for (Round1QueueItem i : queue.findBySessionId(s.id)) {
            if (i.completed) {
                solved.add(i.question.id);
            }
        }
        CrosswordView grid = crosswords.view(solved, false);
        return new Round1State(summaryOf(s), view, grid, lastResult);
    }

    private Round1Summary summaryOf(Round1Session s) {
        String status = s.status;
        if ("IN_PROGRESS".equals(status) && isClosed()) {
            status = "CLOSED";
        }
        return new Round1Summary(status, s.correctCount, (int) questions.count(), s.correctCount,
                s.incorrectCount, s.timeoutCount, s.score);
    }
}
