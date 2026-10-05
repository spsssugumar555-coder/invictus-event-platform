package com.invictus.platform.service;

import com.invictus.platform.dto.AdminDtos.QuestionAdminDto;
import com.invictus.platform.dto.AdminDtos.QuestionRequest;
import com.invictus.platform.dto.AdminDtos.QuestionSetDto;
import com.invictus.platform.dto.AdminDtos.TeamSequenceRow;
import com.invictus.platform.entity.Question;
import com.invictus.platform.entity.Round1Config;
import com.invictus.platform.entity.Team;
import com.invictus.platform.entity.TeamSequenceItem;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.QuestionRepository;
import com.invictus.platform.repository.Round1AttemptRepository;
import com.invictus.platform.repository.Round1ConfigRepository;
import com.invictus.platform.repository.Round1QueueItemRepository;
import com.invictus.platform.repository.Round1SessionRepository;
import com.invictus.platform.repository.TeamRepository;
import com.invictus.platform.repository.TeamSequenceItemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ADMIN-only management of the Round 1 question set, the crossword layout and per-team sequences. */
@Service
public class AdminRound1Service {
    private final QuestionRepository questions;
    private final Round1ConfigRepository config;
    private final Round1AttemptRepository attempts;
    private final Round1QueueItemRepository queue;
    private final Round1SessionRepository sessions;
    private final TeamSequenceItemRepository sequences;
    private final TeamRepository teams;
    private final CrosswordService crosswords;

    public AdminRound1Service(QuestionRepository questions, Round1ConfigRepository config,
                              Round1AttemptRepository attempts, Round1QueueItemRepository queue,
                              Round1SessionRepository sessions, TeamSequenceItemRepository sequences,
                              TeamRepository teams, CrosswordService crosswords) {
        this.questions = questions;
        this.config = config;
        this.attempts = attempts;
        this.queue = queue;
        this.sessions = sessions;
        this.sequences = sequences;
        this.teams = teams;
        this.crosswords = crosswords;
    }

    // ------------------------------------------------------------ question set

    @Transactional(readOnly = true)
    public QuestionSetDto set() {
        Round1Config c = cfg();
        List<QuestionAdminDto> list = questions.findAllByOrderByNumberAsc().stream().map(AdminRound1Service::dto).toList();
        boolean layoutReady = c.gridRows > 0 && list.stream().allMatch(q -> q.row() != null);
        return new QuestionSetDto(c.published, layoutReady, c.gridRows, c.gridCols, list,
                layoutReady ? crosswords.view(Set.of(), true) : null, sessions.count() > 0);
    }

    @Transactional
    public QuestionAdminDto create(QuestionRequest r) {
        requireEditable();
        Question q = new Question();
        q.number = questions.findTopByOrderByNumberDesc().map(x -> x.number).orElse(0) + 1;
        q.direction = "ACROSS";
        apply(q, r);
        clearLayout();
        return dto(questions.save(q));
    }

    @Transactional
    public QuestionAdminDto update(Long id, QuestionRequest r) {
        requireEditable();
        Question q = find(id);
        apply(q, r);
        clearLayout();
        return dto(q);
    }

    @Transactional
    public void delete(Long id) {
        requireEditable();
        Question q = find(id);
        if (attempts.existsByQuestionId(id) || queue.existsByQuestionId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This question already has Round 1 history and cannot be deleted");
        }
        sequences.deleteAllForQuestion(id);
        questions.delete(q);
        clearLayout();
    }

    /** Sets question numbers to the order of the supplied ids (must list every question exactly once). */
    @Transactional
    public QuestionSetDto reorder(List<Long> ids) {
        requireEditable();
        List<Question> all = questions.findAllByOrderByNumberAsc();
        Set<Long> have = all.stream().map(q -> q.id).collect(Collectors.toSet());
        if (ids.size() != all.size() || !have.equals(new HashSet<>(ids))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The order must list every question exactly once");
        }
        // two phases so the UNIQUE(question_number) constraint is never violated mid-update
        for (Question q : all) {
            q.number = (int) -q.id.longValue();
        }
        questions.flush();
        Map<Long, Question> byId = new HashMap<>();
        all.forEach(q -> byId.put(q.id, q));
        int n = 1;
        for (Long id : ids) {
            byId.get(id).number = n++;
        }
        questions.flush();
        clearLayout();
        return set();
    }

    /** Generates (and stores) the crossword layout from the current answers without publishing. */
    @Transactional
    public QuestionSetDto generate() {
        requireEditable();
        generateLayout();
        return set();
    }

    /** Generates the layout and opens Round 1 to players. */
    @Transactional
    public QuestionSetDto publish() {
        Round1Config c = cfg();
        if (c.published) {
            return set();
        }
        generateLayout();
        c.published = true;
        c.publishedAt = Instant.now();
        return set();
    }

    @Transactional
    public QuestionSetDto unpublish() {
        if (sessions.count() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Teams have already started Round 1, so the set can no longer be unpublished");
        }
        Round1Config c = cfg();
        c.published = false;
        c.publishedAt = null;
        return set();
    }

    // ------------------------------------------------------------ team sequences

    @Transactional(readOnly = true)
    public List<TeamSequenceRow> teamSequences() {
        Map<Long, List<Integer>> byTeam = new HashMap<>();
        for (TeamSequenceItem i : sequences.findAll().stream().sorted(Comparator.comparingInt((TeamSequenceItem t) -> t.sortPos)).toList()) {
            byTeam.computeIfAbsent(i.team.id, k -> new ArrayList<>()).add(i.question.number);
        }
        Set<Long> started = new HashSet<>();
        sessions.findAll().forEach(s -> started.add(s.team.id));
        return teams.findAll().stream()
                .sorted(Comparator.comparing((Team t) -> t.name.toLowerCase()))
                .map(t -> new TeamSequenceRow(t.id, t.name, started.contains(t.id), byTeam.getOrDefault(t.id, List.of())))
                .toList();
    }

    /** Stores the first questions this team will see, by question number. Remaining questions follow automatically. */
    @Transactional
    public List<TeamSequenceRow> setSequence(Long teamId, List<Integer> numbers) {
        Team team = teams.findById(teamId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
        if (sessions.findByTeamId(teamId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This team has already started Round 1; its sequence is locked");
        }
        Map<Integer, Question> byNumber = new HashMap<>();
        questions.findAllByOrderByNumberAsc().forEach(q -> byNumber.put(q.number, q));
        if (new HashSet<>(numbers).size() != numbers.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A question number appears more than once");
        }
        for (Integer n : numbers) {
            if (!byNumber.containsKey(n)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "There is no question number " + n);
            }
        }
        sequences.deleteAllForTeam(teamId);
        int pos = 1;
        for (Integer n : numbers) {
            TeamSequenceItem item = new TeamSequenceItem();
            item.team = team;
            item.question = byNumber.get(n);
            item.sortPos = pos++;
            sequences.save(item);
        }
        return teamSequences();
    }

    /** Gives every team that has not started a full, staggered sequence (different start for different teams). */
    @Transactional
    public List<TeamSequenceRow> autoAssign() {
        List<Question> ordered = questions.findAllByOrderByNumberAsc();
        int n = ordered.size();
        if (n == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add questions first");
        }
        for (Team t : teams.findAll()) {
            if (sessions.findByTeamId(t.id).isPresent()) {
                continue;
            }
            sequences.deleteAllForTeam(t.id);
            for (int i = 0; i < n; i++) {
                TeamSequenceItem item = new TeamSequenceItem();
                item.team = t;
                item.question = ordered.get(QuestionOrder.numberAt(t.id, i, n) - 1);
                item.sortPos = i + 1;
                sequences.save(item);
            }
        }
        return teamSequences();
    }

    // ------------------------------------------------------------ internals

    private Round1Config cfg() {
        return config.findById(1L).orElseThrow(() -> new IllegalStateException("round1_config row is missing"));
    }

    private void requireEditable() {
        if (cfg().published) {
            throw new ApiException(HttpStatus.CONFLICT, "Unpublish the question set before changing it");
        }
    }

    private Question find(Long id) {
        return questions.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Question not found"));
    }

    private void apply(Question q, QuestionRequest r) {
        String answer = CrosswordGenerator.normalize(r.answer());
        if (answer.length() < 2 || answer.length() > 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Answer must be 2 to 30 letters or digits (spaces and punctuation are ignored)");
        }
        q.clue = r.clue().trim();
        q.answer = answer;
    }

    private void clearLayout() {
        for (Question q : questions.findAllByOrderByNumberAsc()) {
            q.gridRow = null;
            q.gridCol = null;
            q.displayNumber = null;
        }
        Round1Config c = cfg();
        c.gridRows = 0;
        c.gridCols = 0;
    }

    private void generateLayout() {
        List<Question> all = questions.findAllByOrderByNumberAsc();
        if (all.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add at least one question first");
        }
        List<CrosswordGenerator.Word> words = all.stream()
                .map(q -> new CrosswordGenerator.Word(q.id, q.answer)).toList();
        CrosswordGenerator.Layout layout = CrosswordGenerator.generate(words);
        if (!layout.unplaced().isEmpty()) {
            Set<Long> bad = new HashSet<>(layout.unplaced());
            String names = all.stream().filter(q -> bad.contains(q.id))
                    .map(q -> q.answer + " (question " + q.number + ")").collect(Collectors.joining(", "));
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "These answers cannot be fitted into the crossword: " + names
                            + ". Each answer must share a letter with another answer. Change or add words and try again.");
        }
        Map<Long, CrosswordGenerator.Placement> byId = new HashMap<>();
        layout.placements().forEach(p -> byId.put(p.id(), p));
        for (Question q : all) {
            CrosswordGenerator.Placement p = byId.get(q.id);
            q.gridRow = p.row();
            q.gridCol = p.col();
            q.direction = p.across() ? "ACROSS" : "DOWN";
            q.displayNumber = p.number();
        }
        Round1Config c = cfg();
        c.gridRows = layout.rows();
        c.gridCols = layout.cols();
    }

    private static QuestionAdminDto dto(Question q) {
        return new QuestionAdminDto(q.id, q.number, q.clue, q.answer, q.direction, q.displayNumber, q.gridRow, q.gridCol);
    }
}
