package com.invictus.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invictus.platform.ai.AiClient;
import com.invictus.platform.entity.Round1Attempt;
import com.invictus.platform.repository.Round1AttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Round 1: admin-managed questions, generated crossword, per-team sequences, queue semantics
 * (correct = removed, wrong = same question stays active on the same deadline, timeout = end of queue), stale attempts, and the 120 second timer.
 * Own application context (and in-memory DB), and ordered, because test 1 publishes the question set.
 */
@SpringBootTest(properties = "test.isolated=round1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Round1QueueTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired Round1AttemptRepository attempts;
    @MockBean AiClient ai;

    static TimeZone originalZone;
    TestSupport t;
    String admin;

    // Run under a non-UTC zone to catch timezone round-trip bugs in timestamps.
    @BeforeAll
    static void useKolkata() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @BeforeEach
    void setUp() throws Exception {
        t = new TestSupport(mvc, om, attempts);
        admin = t.adminToken();
    }

    private Map<Integer, String> clueByNumber() throws Exception {
        Map<Integer, String> m = new HashMap<>();
        for (JsonNode q : t.json(t.getReq("/api/admin/round1/questions", admin)).get("questions")) {
            m.put(q.get("number").asInt(), q.get("clue").asText());
        }
        return m;
    }

    @Test
    @Order(1)
    void adminCreatesQuestionsPublishesCrosswordAndConfiguresSequences() throws Exception {
        // sample questions from the seed exist and nothing is published yet
        JsonNode initial = t.json(t.getReq("/api/admin/round1/questions", admin));
        assertFalse(initial.get("published").asBoolean());
        String early = t.register("Early Team", "early@test.com");
        JsonNode notPublished = t.json(t.getReq("/api/round1/current", early));
        assertEquals("NOT_PUBLISHED", notPublished.at("/summary/status").asText());
        assertTrue(notPublished.at("/question").isNull());

        // admin replaces the sample set with their own questions
        for (JsonNode q : initial.get("questions")) {
            t.deleteReq("/api/admin/round1/questions/" + q.get("id").asLong(), admin).andExpect(status().isNoContent());
        }
        List<Long> ids = new ArrayList<>();
        for (String[] w : TestSupport.WORDS) {
            ids.add(t.json(t.postJson("/api/admin/round1/questions", Map.of("clue", w[0], "answer", w[1]), admin)
                    .andExpect(status().isCreated())).get("id").asLong());
        }
        // invalid answer
        t.postJson("/api/admin/round1/questions", Map.of("clue", "x", "answer", "a"), admin).andExpect(status().isBadRequest());

        // edit and reorder (reverse)
        t.putJson("/api/admin/round1/questions/" + ids.get(0), Map.of("clue", TestSupport.WORDS[0][0], "answer", "java"), admin)
                .andExpect(status().isOk());
        List<Long> reversed = new ArrayList<>(ids);
        java.util.Collections.reverse(reversed);
        JsonNode reordered = t.json(t.putJson("/api/admin/round1/questions/order", Map.of("questionIds", reversed), admin)
                .andExpect(status().isOk()));
        assertEquals(reversed.get(0).longValue(), reordered.at("/questions/0/id").asLong());
        assertEquals(1, reordered.at("/questions/0/number").asInt());
        assertEquals(6, reordered.at("/questions/5/number").asInt());
        t.putJson("/api/admin/round1/questions/order", Map.of("questionIds", List.of(ids.get(0))), admin)
                .andExpect(status().isBadRequest());

        // an answer that shares no letters with the others cannot be placed: publishing is refused with a clear reason
        long loner = t.json(t.postJson("/api/admin/round1/questions", Map.of("clue", "Unplaceable", "answer", "QQQQ"), admin)).get("id").asLong();
        String refusal = t.body(t.postJson("/api/admin/round1/publish", Map.of(), admin).andExpect(status().isUnprocessableEntity()));
        assertTrue(refusal.contains("QQQQ"));
        t.deleteReq("/api/admin/round1/questions/" + loner, admin).andExpect(status().isNoContent());

        // publish: the crossword is generated and stored
        JsonNode published = t.json(t.postJson("/api/admin/round1/publish", Map.of(), admin).andExpect(status().isOk()));
        assertTrue(published.get("published").asBoolean());
        assertTrue(published.get("rows").asInt() > 0 && published.get("cols").asInt() > 0);
        assertEquals(published.get("rows").asInt() * published.get("cols").asInt(), published.at("/crossword/cells").size());
        for (JsonNode q : published.get("questions")) {
            assertFalse(q.get("row").isNull(), "every question must be placed in the grid");
            assertTrue(q.get("displayNumber").asInt() >= 1);
        }
        // generation is deterministic: same words, same layout
        JsonNode again = t.json(t.getReq("/api/admin/round1/questions", admin));
        assertEquals(published.get("crossword"), again.get("crossword"));
        // a published set is frozen
        t.postJson("/api/admin/round1/questions", Map.of("clue", "Late", "answer", "LATE"), admin).andExpect(status().isConflict());

        // per-team sequences, set before the team starts
        String t1 = t.register("Seq Team One", "seq1@test.com");
        String t2 = t.register("Seq Team Two", "seq2@test.com");
        Map<Integer, String> clues = clueByNumber();
        JsonNode teams = t.json(t.getReq("/api/admin/round1/teams", admin));
        long id1 = 0;
        long id2 = 0;
        for (JsonNode row : teams) {
            if (row.get("teamName").asText().equals("Seq Team One")) id1 = row.get("teamId").asLong();
            if (row.get("teamName").asText().equals("Seq Team Two")) id2 = row.get("teamId").asLong();
        }
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", id1, "questionNumbers", List.of(5, 2, 3)), admin).andExpect(status().isOk());
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", id2, "questionNumbers", List.of(2, 6, 1)), admin).andExpect(status().isOk());
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", id2, "questionNumbers", List.of(2, 2)), admin).andExpect(status().isBadRequest());
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", id2, "questionNumbers", List.of(99)), admin).andExpect(status().isBadRequest());

        JsonNode first1 = t.json(t.getReq("/api/round1/current", t1));
        JsonNode first2 = t.json(t.getReq("/api/round1/current", t2));
        assertEquals(clues.get(5), first1.at("/question/clue").asText());
        assertEquals(clues.get(2), first2.at("/question/clue").asText());
        assertNotEquals(first1.at("/question/clue").asText(), first2.at("/question/clue").asText());
        // the configured order continues 5 -> 2 -> 3 as each question is solved
        long firstAttempt = first1.at("/question/attemptId").asLong();
        JsonNode s2 = t.submit(t1, firstAttempt, t.realAnswer(firstAttempt));
        assertEquals(clues.get(2), s2.at("/question/clue").asText());
        long secondAttempt = s2.at("/question/attemptId").asLong();
        JsonNode s3 = t.submit(t1, secondAttempt, t.realAnswer(secondAttempt));
        assertEquals(clues.get(3), s3.at("/question/clue").asText());
        // once a team has started, its sequence is locked
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", id1, "questionNumbers", List.of(1)), admin).andExpect(status().isConflict());
        // players cannot change sequences
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", id1, "questionNumbers", List.of(1)), t1).andExpect(status().isForbidden());
    }

    @Test
    @Order(2)
    void playerNeverReceivesAnswersAndSeesTheWholeCrossword() throws Exception {
        t.ensurePublished(admin);
        String token = t.register("Privacy Team", "privacy@test.com");
        String raw = t.body(t.getReq("/api/round1/current", token).andExpect(status().isOk()));
        String lower = raw.toLowerCase();
        for (String[] w : TestSupport.WORDS) {
            assertFalse(lower.contains(w[1].toLowerCase()), "response leaked answer " + w[1]);
        }
        assertFalse(lower.contains("\"answer\""));
        JsonNode state = om.readTree(raw);
        int open = 0;
        for (JsonNode cell : state.at("/crossword/cells")) {
            if (cell.get("open").asBoolean()) open++;
            assertTrue(cell.get("letter").isNull(), "unsolved letters must not be sent");
        }
        assertTrue(open > 0);
        assertEquals(state.at("/crossword/rows").asInt() * state.at("/crossword/cols").asInt(), state.at("/crossword/cells").size());
        assertFalse(state.has("answers"));
        // no skip / next endpoint exists
        int code = t.postJson("/api/round1/skip", Map.of("attemptId", 1), token).andReturn().getResponse().getStatus();
        assertTrue(code == 404 || code == 405, "skip must not exist, got " + code);
        code = t.postJson("/api/round1/next", Map.of(), token).andReturn().getResponse().getStatus();
        assertTrue(code == 404 || code == 405, "next must not exist, got " + code);
    }

    @Test
    @Order(3)
    void wrongAnswerKeepsTheSameQuestionActiveOnTheSameDeadline() throws Exception {
        t.ensurePublished(admin);
        String token = t.register("Wrong Team", "wrong@test.com");
        JsonNode cur = t.json(t.getReq("/api/round1/current", token));
        long attemptId = cur.at("/question/attemptId").asLong();
        long questionId = t.questionIdOf(attemptId);
        Instant deadline = attempts.findById(attemptId).orElseThrow().expiresAt;
        long firstRemaining = cur.at("/question/remainingMs").asLong();

        for (int i = 1; i <= 3; i++) {
            JsonNode wrong = t.submit(token, attemptId, "ZZZWRONG");
            assertEquals("INCORRECT", wrong.get("lastResult").asText());
            assertEquals(attemptId, wrong.at("/question/attemptId").asLong(), "same attempt stays active");
            assertEquals(cur.at("/question/clue").asText(), wrong.at("/question/clue").asText());
            assertTrue(wrong.at("/question/remainingMs").asLong() <= firstRemaining, "timer continues, it is not reset");
            assertTrue(wrong.at("/question/remainingMs").asLong() > 0);
            assertEquals(i, wrong.at("/summary/incorrect").asInt());
            assertEquals(0, wrong.at("/summary/completed").asInt());
            assertEquals(0, wrong.at("/summary/timeouts").asInt());
            assertEquals(0, wrong.at("/summary/score").asInt());
        }
        // nothing was closed or moved in the database
        Round1Attempt stored = attempts.findById(attemptId).orElseThrow();
        assertEquals("ACTIVE", stored.status);
        assertEquals(deadline, stored.expiresAt, "deadline is unchanged by wrong answers");
        assertEquals(questionId, t.questionIdOf(t.json(t.getReq("/api/round1/current", token)).at("/question/attemptId").asLong()));

        // the same question can still be answered correctly, and only then is it completed
        JsonNode correct = t.submit(token, attemptId, t.realAnswer(attemptId));
        assertEquals("CORRECT", correct.get("lastResult").asText());
        assertEquals(1, correct.at("/summary/completed").asInt());
        assertEquals(10, correct.at("/summary/score").asInt());
        assertEquals(3, correct.at("/summary/incorrect").asInt());
        assertNotEquals(attemptId, correct.at("/question/attemptId").asLong());
        // a wrong answer after the deadline is a timeout, not a retry
        long next = correct.at("/question/attemptId").asLong();
        Round1Attempt n = attempts.findById(next).orElseThrow();
        n.expiresAt = Instant.now().minusSeconds(1);
        attempts.save(n);
        JsonNode late = t.submit(token, next, "ZZZWRONG");
        assertEquals("TIMEOUT", late.get("lastResult").asText());
        assertEquals(1, late.at("/summary/timeouts").asInt());
    }

    @Test
    @Order(4)
    void timeoutMovesQuestionToTheEndAndStartsAFreshTimer() throws Exception {
        t.ensurePublished(admin);
        String token = t.register("Timeout Team", "timeout@test.com");
        JsonNode cur = t.json(t.getReq("/api/round1/current", token));
        long firstAttempt = cur.at("/question/attemptId").asLong();
        long firstQuestion = t.questionIdOf(firstAttempt);

        Round1Attempt a = attempts.findById(firstAttempt).orElseThrow();
        a.expiresAt = Instant.now().minusSeconds(2);
        attempts.save(a);

        JsonNode after = t.json(t.getReq("/api/round1/current", token));
        assertEquals("TIMEOUT", after.get("lastResult").asText());
        assertEquals(1, after.at("/summary/timeouts").asInt());
        assertEquals(0, after.at("/summary/completed").asInt());
        long secondQuestion = t.questionIdOf(after.at("/question/attemptId").asLong());
        assertNotEquals(firstQuestion, secondQuestion);
        assertTrue(after.at("/question/remainingMs").asLong() > 110_000, "next question gets a fresh timer");

        // the timed-out question is at the END: it is served only after the other 5 are solved
        JsonNode state = after;
        for (int i = 0; i < 5; i++) {
            long id = state.at("/question/attemptId").asLong();
            assertNotEquals(firstQuestion, t.questionIdOf(id), "timed-out question must not come back early");
            state = t.submit(token, id, t.realAnswer(id));
        }
        assertEquals(firstQuestion, t.questionIdOf(state.at("/question/attemptId").asLong()));
    }

    @Test
    @Order(5)
    void correctAnswersAreRemovedForeverAndRoundCompletesOnlyWhenAllAreCorrect() throws Exception {
        t.ensurePublished(admin);
        String token = t.register("Loop Team", "loop@test.com");
        JsonNode state = t.json(t.getReq("/api/round1/current", token));
        Set<Long> done = new HashSet<>();
        Set<Long> timedOutOnce = new HashSet<>();
        for (int i = 0; i < 100 && !state.at("/question").isNull(); i++) {
            long attemptId = state.at("/question/attemptId").asLong();
            long qid = t.questionIdOf(attemptId);
            assertFalse(done.contains(qid), "a completed question must never be served again");
            if (timedOutOnce.add(qid)) {
                // let the first sighting of every question time out: it must come back later
                Round1Attempt a = attempts.findById(attemptId).orElseThrow();
                a.expiresAt = Instant.now().minusSeconds(1);
                attempts.save(a);
                state = t.json(t.getReq("/api/round1/current", token));
                assertEquals("TIMEOUT", state.get("lastResult").asText());
            } else {
                // a wrong answer first: same attempt stays; then the correct one completes it
                JsonNode wrong = t.submit(token, attemptId, "ZZZWRONG");
                assertEquals(attemptId, wrong.at("/question/attemptId").asLong());
                state = t.submit(token, attemptId, t.realAnswer(attemptId));
                done.add(qid);
                if (done.size() == 5) {
                    assertEquals("IN_PROGRESS", state.at("/summary/status").asText(), "5 of 6 correct is not complete");
                    assertFalse(state.at("/question").isNull());
                }
            }
        }
        assertEquals(6, done.size());
        assertEquals("COMPLETED", state.at("/summary/status").asText());
        assertTrue(state.at("/question").isNull());
        assertEquals(60, state.at("/summary/score").asInt());
        assertEquals(6, state.at("/summary/completed").asInt());
        assertEquals(6, state.at("/summary/incorrect").asInt());
        assertEquals(6, state.at("/summary/timeouts").asInt());
        // solved words are revealed to this team on the grid
        int letters = 0;
        for (JsonNode cell : state.at("/crossword/cells")) {
            if (!cell.get("letter").isNull()) letters++;
        }
        assertTrue(letters > 0);
        // the finished round does not loop forever or create new attempts
        JsonNode again = t.json(t.getReq("/api/round1/current", token));
        assertEquals("COMPLETED", again.at("/summary/status").asText());
        assertTrue(again.at("/question").isNull());
    }

    @Test
    @Order(6)
    void staleOldAndForeignAttemptsAreRejectedWithoutChangingState() throws Exception {
        t.ensurePublished(admin);
        String a = t.register("Stale Team A", "stalea@test.com");
        String b = t.register("Stale Team B", "staleb@test.com");
        JsonNode curA = t.json(t.getReq("/api/round1/current", a));
        t.getReq("/api/round1/current", b).andExpect(status().isOk());
        long attemptA = curA.at("/question/attemptId").asLong();

        // another team's attempt
        t.postJson("/api/round1/answer", Map.of("attemptId", attemptA, "answer", "X"), b).andExpect(status().isNotFound());
        assertEquals("ACTIVE", attempts.findById(attemptA).orElseThrow().status);

        // an attempt that was already answered
        JsonNode afterA = t.submit(a, attemptA, t.realAnswer(attemptA));
        assertEquals("CORRECT", afterA.get("lastResult").asText());
        t.postJson("/api/round1/answer", Map.of("attemptId", attemptA, "answer", t.realAnswer(attemptA)), a).andExpect(status().isConflict());
        JsonNode status = t.json(t.getReq("/api/round1/status", a));
        assertEquals(1, status.get("correct").asInt());
        assertEquals(10, status.get("score").asInt());

        // an attempt that was timed out
        long next = afterA.at("/question/attemptId").asLong();
        Round1Attempt n = attempts.findById(next).orElseThrow();
        n.expiresAt = Instant.now().minusSeconds(1);
        attempts.save(n);
        t.getReq("/api/round1/current", a).andExpect(status().isOk());
        t.postJson("/api/round1/answer", Map.of("attemptId", next, "answer", "JAVA"), a).andExpect(status().isConflict());
        assertEquals(1, t.json(t.getReq("/api/round1/status", a)).get("timeouts").asInt());
        assertEquals(10, t.json(t.getReq("/api/round1/status", a)).get("score").asInt());
    }

    @Test
    @Order(7)
    void timerIsServerAuthoritativeAndSurvivesRefresh() throws Exception {
        t.ensurePublished(admin);
        String token = t.register("Timer Team", "timer@test.com");
        JsonNode first = t.json(t.getReq("/api/round1/current", token));
        long id = first.at("/question/attemptId").asLong();
        long reported = first.at("/question/remainingMs").asLong();
        assertEquals(120_000, first.at("/question/totalMs").asLong());
        assertTrue(reported > 110_000 && reported <= 120_000);

        // stored deadline is exactly 120s after the start and matches what the API reported (re-read from the DB)
        Round1Attempt stored = attempts.findById(id).orElseThrow();
        assertEquals(Duration.ofSeconds(120), Duration.between(stored.startedAt, stored.expiresAt));
        long fromDb = Duration.between(Instant.now(), stored.expiresAt).toMillis();
        assertTrue(Math.abs(fromDb - reported) < 5_000, "stored expiresAt implies " + fromDb + "ms but API said " + reported);

        // a refresh (second GET) returns the SAME attempt with a smaller-or-equal remaining time, no timeout
        JsonNode second = t.json(t.getReq("/api/round1/current", token));
        assertEquals(id, second.at("/question/attemptId").asLong());
        assertTrue(second.at("/question/remainingMs").asLong() <= reported);
        assertTrue(second.get("lastResult").isNull());
        assertEquals(0, second.at("/summary/timeouts").asInt());

        // answering after the deadline is a TIMEOUT even with the right answer
        String real = t.realAnswer(id);
        stored.expiresAt = Instant.now().minusSeconds(1);
        attempts.save(stored);
        JsonNode late = t.submit(token, id, real);
        assertEquals("TIMEOUT", late.get("lastResult").asText());
        assertEquals(0, late.at("/summary/score").asInt());
        assertEquals(0, late.at("/summary/completed").asInt());
        assertNotNull(late.at("/question/attemptId"));
        assertNotEquals(id, late.at("/question/attemptId").asLong());
    }

    @Test
    @Order(8)
    void freshAttemptWithCorrectAnswerIsCorrect() throws Exception {
        t.ensurePublished(admin);
        String token = t.register("Correct Team", "correct@test.com");
        JsonNode r = t.answerCurrent(token, true);
        assertEquals("CORRECT", r.get("lastResult").asText());
        assertEquals(10, r.at("/summary/score").asInt());
        assertEquals(0, r.at("/summary/timeouts").asInt());
    }

    @Test
    @Order(9)
    void freshTeamStartsItselfAtTheFirstQuestionOfItsSequenceAndRefreshKeepsOneSession() throws Exception {
        t.ensurePublished(admin);
        String token = t.register("Hello Team", "hello@test.com");
        // the same flow the organisers use: staggered auto-assign for teams that have not started
        JsonNode rows = t.json(t.postJson("/api/admin/round1/team-sequence/auto", Map.of(), admin).andExpect(status().isOk()));
        int firstNumber = -1;
        for (JsonNode r : rows) {
            if (r.get("teamName").asText().equals("Hello Team")) {
                assertFalse(r.get("sessionStarted").asBoolean(), "a fresh team has no session yet");
                firstNumber = r.get("questionNumbers").get(0).asInt();
            }
        }
        assertTrue(firstNumber > 0);
        String expectedClue = clueByNumber().get(firstNumber);

        // the team starts its own round on first load: first clue of its sequence, fresh 120s server timer
        JsonNode first = t.json(t.getReq("/api/round1/current", token).andExpect(status().isOk()));
        assertEquals("IN_PROGRESS", first.at("/summary/status").asText());
        assertEquals(expectedClue, first.at("/question/clue").asText());
        assertTrue(first.at("/question/remainingMs").asLong() > 110_000);
        assertFalse(first.at("/crossword").isNull());
        assertFalse(t.body(t.getReq("/api/round1/current", token)).toLowerCase().contains("\"answer\""));
        long attemptId = first.at("/question/attemptId").asLong();

        // refreshing does not create another session or reset the timer
        JsonNode again = t.json(t.getReq("/api/round1/current", token));
        assertEquals(attemptId, again.at("/question/attemptId").asLong());
        assertTrue(again.at("/question/remainingMs").asLong() <= first.at("/question/remainingMs").asLong());
        int sessionsForTeam = 0;
        for (JsonNode row : t.json(t.getReq("/api/admin/round1", admin))) {
            if (row.get("teamName").asText().equals("Hello Team")) sessionsForTeam++;
        }
        assertEquals(1, sessionsForTeam);

        // once started the sequence is locked
        long teamId = 0;
        for (JsonNode r : rows) {
            if (r.get("teamName").asText().equals("Hello Team")) teamId = r.get("teamId").asLong();
        }
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", teamId, "questionNumbers", List.of(1)), admin)
                .andExpect(status().isConflict());
    }
}
