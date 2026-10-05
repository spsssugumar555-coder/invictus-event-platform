package com.invictus.platform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invictus.platform.ai.AiClient;
import com.invictus.platform.ai.AiEvaluationService;
import com.invictus.platform.ai.GeminiException;
import com.invictus.platform.repository.Round1AttemptRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Finalizing Round 1 is global state, so this class gets its own application context. */
@SpringBootTest(properties = "test.isolated=qualification")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class QualificationAndRound2Test {
    static final String GOOD_JSON = """
            {"requirementCompletion":8,"functionality":8,"uiQuality":8,"responsiveness":8,
             "technicalQuality":8,"accuracy":8,"feedback":"Solid work with clear structure."}
            """;
    static final String HTML = "<!DOCTYPE html><html><body><h1>Recreated</h1></body></html>";
    static final String INTERNAL_MARKER = "INTERNAL-EVAL-MARKER";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired Round1AttemptRepository attempts;
    @MockBean AiClient ai;

    @Test
    void finalScoreIsComputedByTheBackendFromWeights() {
        assertEquals(80.0, AiEvaluationService.finalScore(8, 8, 8, 8, 8, 8));
        assertEquals(100.0, AiEvaluationService.finalScore(10, 10, 10, 10, 10, 10));
        assertEquals(0.0, AiEvaluationService.finalScore(0, 0, 0, 0, 0, 0));
    }

    @Test
    void onlyTeamsThatCompleteRound1CanQualifyAndRound2StaysPrivate() throws Exception {
        TestSupport t = new TestSupport(mvc, om, attempts);
        String admin = t.adminToken();
        t.ensurePublished(admin);

        // Teams 1..11 complete Round 1 one after another; team 12 only gets half way.
        List<String> tokens = new ArrayList<>();
        for (int k = 1; k <= 11; k++) {
            String token = t.register("Team " + k, "team" + k + "@test.com");
            tokens.add(token);
            t.completeRound1(token);
            assertEquals("COMPLETED", t.json(t.getReq("/api/round1/status", token)).get("status").asText());
        }
        String partial = t.register("Team 12", "team12@test.com");
        tokens.add(partial);
        for (int i = 0; i < 3; i++) {
            t.answerCurrent(partial, true);
        }
        assertEquals("IN_PROGRESS", t.json(t.getReq("/api/round1/status", partial)).get("status").asText());

        // admin progress view
        JsonNode progress = t.json(t.getReq("/api/admin/round1", admin));
        assertEquals(12, progress.size());
        assertTrue(progress.get(0).has("timeouts") && progress.get(0).has("incorrect") && !progress.get(0).has("skipped"));
        t.getReq("/api/admin/round1", tokens.get(0)).andExpect(status().isForbidden());

        // players cannot finalize
        t.postJson("/api/admin/qualifications/finalize", Map.of(), tokens.get(0)).andExpect(status().isForbidden());
        t.postJson("/api/admin/qualifications/finalize", Map.of(), admin).andExpect(status().isOk());

        // existing ranking order (score, correct, earliest completion): team 1 finished first
        JsonNode first = t.json(t.getReq("/api/player/dashboard", tokens.get(0)));
        assertEquals("QUALIFIED", first.get("qualificationStatus").asText());
        assertEquals(1, first.get("rank").asInt());
        assertEquals("ROUND_2", first.get("currentRound").asText());
        JsonNode tenth = t.json(t.getReq("/api/player/dashboard", tokens.get(9)));
        assertEquals("QUALIFIED", tenth.get("qualificationStatus").asText());
        assertEquals(10, tenth.get("rank").asInt());
        JsonNode eleventh = t.json(t.getReq("/api/player/dashboard", tokens.get(10)));
        assertEquals("NOT_QUALIFIED", eleventh.get("qualificationStatus").asText());
        assertEquals("NOT_QUALIFIED", eleventh.get("round2Status").asText());
        JsonNode incomplete = t.json(t.getReq("/api/player/dashboard", partial));
        assertEquals("NOT_QUALIFIED", incomplete.get("qualificationStatus").asText());
        assertTrue(incomplete.get("rank").isNull());

        // Round 1 is closed after finalization
        JsonNode closed = t.json(t.getReq("/api/round1/current", partial));
        assertEquals("CLOSED", closed.at("/summary/status").asText());
        assertTrue(closed.at("/question").isNull());

        // non-qualified teams are locked out of every Round 2 route
        String locked = tokens.get(10);
        t.getReq("/api/round2", locked).andExpect(status().isForbidden());
        t.getReq("/api/round2/reference-image", locked).andExpect(status().isForbidden());
        t.postJson("/api/round2/generate", Map.of("instruction", "x"), locked).andExpect(status().isForbidden());
        t.postJson("/api/round2/submit", Map.of(), locked).andExpect(status().isForbidden());

        String winner = tokens.get(0);
        String second = tokens.get(1);

        // challenge is not visible until the admin publishes it
        t.getReq("/api/round2", winner).andExpect(status().isConflict());

        // ----- admin sets up Round 2
        JsonNode blank = t.json(t.getReq("/api/admin/round2/challenge", admin));
        assertFalse(blank.get("published").asBoolean());
        t.postJson("/api/admin/round2/publish", Map.of(), admin).andExpect(status().isBadRequest()); // no image yet
        t.putJson("/api/admin/round2/challenge", Map.of("title", "Recreate the page",
                "prompt", "Recreate the reference page as closely as possible.",
                "requirements", "Match layout and colors.",
                "evaluationPrompt", "Judge fidelity strictly. " + INTERNAL_MARKER,
                "timeLimitMinutes", 90), admin).andExpect(status().isOk());
        byte[] png = new byte[64];
        Arrays.fill(png, (byte) 7);
        System.arraycopy(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, png, 0, 8);
        t.upload("/api/admin/round2/reference-image", "ref.png", new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14}, admin)
                .andExpect(status().isUnsupportedMediaType()); // not a real image
        t.upload("/api/admin/round2/reference-image", "ref.png", png, admin).andExpect(status().isOk());
        JsonNode adminView = t.json(t.postJson("/api/admin/round2/publish", Map.of(), admin).andExpect(status().isOk()));
        assertTrue(adminView.get("published").asBoolean());
        assertTrue(adminView.get("evaluationPrompt").asText().contains(INTERNAL_MARKER)); // admin sees both prompts
        assertTrue(adminView.get("prompt").asText().contains("Recreate the reference page"));
        t.getReq("/api/admin/round2/challenge", winner).andExpect(status().isForbidden());
        t.putJson("/api/admin/round2/challenge", Map.of("title", "x", "prompt", "x", "evaluationPrompt", "x", "timeLimitMinutes", 1), winner)
                .andExpect(status().isForbidden());

        // ----- qualified player sees the participant-facing data only
        String view = t.body(t.getReq("/api/round2", winner).andExpect(status().isOk()));
        assertTrue(view.contains("Recreate the reference page"));
        assertFalse(view.contains(INTERNAL_MARKER));
        assertFalse(view.toLowerCase().contains("evaluationprompt"));
        byte[] served = t.getReq("/api/round2/reference-image", winner).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, served);

        // ----- Gemini builder runs on the server; nothing sensitive reaches the model prompt either
        // ----- 10 qualified teams use the AI builder at the same time; 3 of them hit a Gemini outage.
        // Each request is isolated: failures do not touch other teams, and each team gets only its own draft.
        doAnswer(inv -> {
            String p = inv.getArgument(1);
            if (p.contains("FAIL-503")) {
                throw new GeminiException(GeminiException.Kind.UNAVAILABLE, 503, "Gemini HTTP 503: overloaded");
            }
            Matcher m = Pattern.compile("ok team-(\\d+)").matcher(p);
            Thread.sleep(100);
            return "<!DOCTYPE html><html><body>marker-" + (m.find() ? m.group(1) : "none") + "</body></html>";
        }).when(ai).generate(anyString(), anyString(), any(), any(), eq(false));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Integer>> codes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final String token = tokens.get(i);
            final String instruction = i <= 2 ? "FAIL-503 team-" + i : "ok team-" + i;
            codes.add(pool.submit(() -> {
                startGun.await();
                return t.postJson("/api/round2/generate", Map.of("instruction", instruction), token).andReturn().getResponse().getStatus();
            }));
        }
        startGun.countDown();
        for (int i = 0; i < 10; i++) {
            assertEquals(i <= 2 ? 503 : 200, codes.get(i).get(30, TimeUnit.SECONDS), "team index " + i);
        }
        pool.shutdown();
        for (int i = 0; i < 10; i++) {
            JsonNode v = t.json(t.getReq("/api/round2", tokens.get(i)));
            if (i <= 2) {
                assertTrue(v.get("draftHtml").isNull(), "a failed team has no draft");
                assertEquals(40, v.get("generationsLeft").asInt(), "a failed generation is not counted");
            } else {
                assertTrue(v.get("draftHtml").asText().contains("marker-" + i), "team " + i + " received its own page");
                assertEquals(39, v.get("generationsLeft").asInt());
            }
        }
        String outage = t.body(t.postJson("/api/round2/generate", Map.of("instruction", "FAIL-503 again"), tokens.get(0)).andExpect(status().isServiceUnavailable()));
        assertEquals("Gemini is temporarily unavailable. Please try again shortly.", om.readTree(outage).get("message").asText());

        doReturn("```html\n" + HTML + "\n```").when(ai).generate(anyString(), anyString(), any(), any(), eq(false));
        t.postJson("/api/round2/submit", Map.of(), second).andExpect(status().isBadRequest()); // nothing generated yet
        JsonNode gen = t.json(t.postJson("/api/round2/generate", Map.of("instruction", "make the header blue"), winner).andExpect(status().isOk()));
        assertEquals(HTML, gen.get("html").asText());
        assertEquals(39, gen.get("generationsLeft").asInt());
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> image = ArgumentCaptor.forClass(byte[].class);
        verify(ai, atLeastOnce()).generate(system.capture(), prompt.capture(), image.capture(), any(), eq(false));
        assertFalse(system.getValue().contains(INTERNAL_MARKER));
        assertFalse(prompt.getValue().contains(INTERNAL_MARKER));
        assertTrue(prompt.getValue().contains("make the header blue"));
        assertArrayEquals(png, image.getValue());
        // refresh keeps the draft
        assertEquals(HTML, t.json(t.getReq("/api/round2", winner)).get("draftHtml").asText());

        // ----- submit; the player gets a bare confirmation, never an evaluation
        doReturn(GOOD_JSON).when(ai).generate(anyString(), anyString(), any(), any(), eq(true));
        String submitBody = t.postJson("/api/round2/submit", Map.of("notes", "done"), winner)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertDoesNotLeak(submitBody);
        long submissionId = om.readTree(submitBody).get("id").asLong();
        String after = t.body(t.getReq("/api/round2", winner));
        assertDoesNotLeak(after);
        assertTrue(om.readTree(after).get("draftHtml").isNull());
        assertDoesNotLeak(t.body(t.getReq("/api/player/dashboard", winner)));
        t.postJson("/api/round2/submit", Map.of(), winner).andExpect(status().isConflict());
        t.postJson("/api/round2/generate", Map.of("instruction", "again"), winner).andExpect(status().isConflict());

        // the evaluation call used the INTERNAL prompt, the reference image and the team's code
        ArgumentCaptor<String> evalSystem = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> evalPrompt = ArgumentCaptor.forClass(String.class);
        verify(ai, atLeastOnce()).generate(evalSystem.capture(), evalPrompt.capture(), any(), any(), eq(true));
        assertTrue(evalSystem.getValue().contains(INTERNAL_MARKER));
        assertTrue(evalPrompt.getValue().contains("<h1>Recreated</h1>"));

        // ----- players cannot reach admin results, other teams, or evaluation endpoints
        t.getReq("/api/admin/round2/submissions/" + submissionId, winner).andExpect(status().isForbidden());
        t.getReq("/api/admin/round2/submissions", winner).andExpect(status().isForbidden());
        t.getReq("/api/admin/round2/results", winner).andExpect(status().isForbidden());
        t.postJson("/api/admin/round2/evaluate", Map.of("submissionId", submissionId), winner).andExpect(status().isForbidden());
        assertFalse(t.body(t.getReq("/api/round2", second)).contains("Recreated")); // another team's page is never returned

        // ----- admin sees everything
        JsonNode detail = t.json(t.getReq("/api/admin/round2/submissions/" + submissionId, admin));
        assertEquals("Team 1", detail.get("teamName").asText());
        assertTrue(detail.get("generatedHtml").asText().contains("Recreated"));
        assertEquals("COMPLETED", detail.at("/evaluation/status").asText());
        assertEquals(80.0, detail.at("/evaluation/finalScore").asDouble());
        assertEquals(8, detail.at("/evaluation/accuracyScore").asInt());
        assertTrue(detail.at("/evaluation/feedback").asText().contains("Solid"));
        assertTrue(detail.at("/evaluation/rawResponse").asText().contains("requirementCompletion"));
        JsonNode results = t.json(t.getReq("/api/admin/round2/results", admin));
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).get("rank").asInt());
        assertEquals(80.0, results.get(0).get("finalScore").asDouble());

        // ----- Gemini down: submission still succeeds, evaluation FAILED, admin retry works
        doThrow(new IllegalStateException("Gemini unavailable")).when(ai).generate(anyString(), anyString(), any(), any(), eq(true));
        t.postJson("/api/round2/generate", Map.of("instruction", "build it"), second).andExpect(status().isOk());
        String body2 = t.postJson("/api/round2/submit", Map.of(), second).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id2 = om.readTree(body2).get("id").asLong();
        assertEquals("FAILED", t.json(t.getReq("/api/admin/round2/submissions/" + id2, admin)).at("/evaluation/status").asText());
        doReturn(GOOD_JSON).when(ai).generate(anyString(), anyString(), any(), any(), eq(true));
        assertEquals("COMPLETED", t.json(t.postJson("/api/admin/round2/evaluate", Map.of("submissionId", id2), admin)).at("/evaluation/status").asText());
        // the judge is allowed to wrap its JSON in a markdown fence
        doReturn("```json\n" + GOOD_JSON + "\n```").when(ai).generate(anyString(), anyString(), any(), any(), eq(true));
        assertEquals("COMPLETED", t.json(t.postJson("/api/admin/round2/evaluate", Map.of("submissionId", id2), admin)).at("/evaluation/status").asText());
        doReturn("{\"requirementCompletion\": 99}").when(ai).generate(anyString(), anyString(), any(), any(), eq(true));
        assertEquals("FAILED", t.json(t.postJson("/api/admin/round2/evaluate", Map.of("submissionId", id2), admin)).at("/evaluation/status").asText());

        // builder failure is reported cleanly and does not use up an attempt
        doThrow(new IllegalStateException("down")).when(ai).generate(anyString(), anyString(), any(), any(), eq(false));
        String third = tokens.get(2);
        t.postJson("/api/round2/generate", Map.of("instruction", "x"), third).andExpect(status().isBadGateway());
        assertEquals(40, t.json(t.getReq("/api/round2", third)).get("generationsLeft").asInt());

        // classified Gemini failures become clean application messages, never raw exceptions
        doThrow(new GeminiException(GeminiException.Kind.AUTH, 403, "Gemini HTTP 403: denied")).when(ai).generate(anyString(), anyString(), any(), any(), eq(false));
        String authBody = t.body(t.postJson("/api/round2/generate", Map.of("instruction", "x"), third).andExpect(status().isInternalServerError()));
        assertTrue(authBody.contains("not configured correctly"));
        assertFalse(authBody.contains("403") || authBody.contains("denied") || authBody.contains("Exception"));
        doThrow(new GeminiException(GeminiException.Kind.MODEL_NOT_FOUND, 404, "Gemini HTTP 404: no such model")).when(ai).generate(anyString(), anyString(), any(), any(), eq(false));
        String modelBody = t.body(t.postJson("/api/round2/generate", Map.of("instruction", "x"), third).andExpect(status().isInternalServerError()));
        assertTrue(modelBody.contains("model"));
        assertFalse(modelBody.contains("no such model"));
        doThrow(new GeminiException(GeminiException.Kind.RATE_LIMITED, 429, "Gemini HTTP 429: quota")).when(ai).generate(anyString(), anyString(), any(), any(), eq(false));
        assertEquals("Gemini is temporarily unavailable. Please try again shortly.",
                om.readTree(t.body(t.postJson("/api/round2/generate", Map.of("instruction", "x"), third).andExpect(status().isServiceUnavailable()))).get("message").asText());

        // admin dashboard numbers
        JsonNode stats = t.json(t.getReq("/api/admin/dashboard", admin));
        assertEquals(12, stats.get("totalTeams").asInt());
        assertEquals(10, stats.get("qualifiedTeams").asInt());
        assertEquals(2, stats.get("round2Submissions").asInt());
        assertEquals(1, stats.get("evaluationsCompleted").asInt());
        // ----- deleting teams that have Round 1 + Round 2 data removes only that team's records
        long secondId = 0;
        long partialId = 0;
        for (JsonNode row : t.json(t.getReq("/api/admin/teams", admin))) {
            if (row.get("name").asText().equals("Team 2")) secondId = row.get("id").asLong();
            if (row.get("name").asText().equals("Team 12")) partialId = row.get("id").asLong();
        }
        t.deleteReq("/api/admin/teams/" + secondId, winner).andExpect(status().isForbidden()); // players cannot delete
        t.deleteReq("/api/admin/teams/" + secondId, admin).andExpect(status().isNoContent());   // submitted + evaluated + qualified
        t.deleteReq("/api/admin/teams/" + partialId, admin).andExpect(status().isNoContent());  // half-finished Round 1
        t.deleteReq("/api/admin/teams/" + secondId, admin).andExpect(status().isNotFound());

        JsonNode afterDelete = t.json(t.getReq("/api/admin/dashboard", admin));
        assertEquals(10, afterDelete.get("totalTeams").asInt());
        assertEquals(9, afterDelete.get("qualifiedTeams").asInt());
        assertEquals(1, afterDelete.get("round2Submissions").asInt());
        JsonNode resultsAfter = t.json(t.getReq("/api/admin/round2/results", admin));
        assertEquals(1, resultsAfter.size());
        assertEquals("Team 1", resultsAfter.get(0).get("teamName").asText());
        assertEquals(80.0, resultsAfter.get(0).get("finalScore").asDouble());
        // the unrelated team's data is intact and still usable
        JsonNode winnerDetail = t.json(t.getReq("/api/admin/round2/submissions/" + submissionId, admin));
        assertEquals("COMPLETED", winnerDetail.at("/evaluation/status").asText());
        t.getReq("/api/round2", winner).andExpect(status().isOk());
        assertEquals("QUALIFIED", t.json(t.getReq("/api/player/dashboard", winner)).get("qualificationStatus").asText());
        for (JsonNode row : t.json(t.getReq("/api/admin/teams", admin))) {
            assertFalse(row.get("name").asText().equals("Team 2") || row.get("name").asText().equals("Team 12"));
        }
        for (JsonNode row : t.json(t.getReq("/api/admin/round1", admin))) {
            assertFalse(row.get("teamName").asText().equals("Team 2") || row.get("teamName").asText().equals("Team 12"));
        }
        t.getReq("/api/admin/rankings", admin).andExpect(status().isOk());
        t.getReq("/api/admin/qualifications", admin).andExpect(status().isOk());
        // the deleted teams can no longer sign in, and their old tokens do not crash anything
        t.postJson("/api/auth/login", Map.of("email", "team2@test.com", "password", "password123"), null).andExpect(status().isUnauthorized());
        t.getReq("/api/player/dashboard", second).andExpect(status().isNotFound());
        t.getReq("/api/round2", second).andExpect(status().isForbidden());
    }


    private static void assertDoesNotLeak(String body) {
        String lower = body.toLowerCase();
        for (String forbidden : List.of("finalscore", "feedback", "evaluation", "accuracy", "uiscore", "gemini",
                "rawresponse", INTERNAL_MARKER.toLowerCase())) {
            assertFalse(lower.contains(forbidden), "player response leaked '" + forbidden + "': " + body);
        }
    }
}
