package com.invictus.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invictus.platform.ai.AiClient;
import com.invictus.platform.repository.Round1AttemptRepository;
import com.invictus.platform.service.QuestionOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired Round1AttemptRepository attempts;
    @MockBean AiClient ai;

    TestSupport t;

    @BeforeEach
    void setUp() {
        t = new TestSupport(mvc, om, attempts);
    }

    @Test
    void registrationAndLogin() throws Exception {
        String token = t.register("Alpha Team", "alpha@test.com");
        assertFalse(token.isBlank());

        // duplicate email and duplicate team name are rejected
        t.postJson("/api/auth/register", Map.of("teamName", "Other", "captainName", "C", "captainEmail", "alpha@test.com",
                "members", List.of(), "password", "password123"), null).andExpect(status().isConflict());
        t.postJson("/api/auth/register", Map.of("teamName", "alpha team", "captainName", "C", "captainEmail", "other@test.com",
                "members", List.of(), "password", "password123"), null).andExpect(status().isConflict());

        // validation
        t.postJson("/api/auth/register", Map.of("teamName", "Weak", "captainName", "C", "captainEmail", "weak@test.com",
                "members", List.of(), "password", "short"), null).andExpect(status().isBadRequest());
        t.postJson("/api/auth/register", Map.of("teamName", "BadMail", "captainName", "C", "captainEmail", "not-an-email",
                "members", List.of(), "password", "password123"), null).andExpect(status().isBadRequest());

        // login
        t.postJson("/api/auth/login", Map.of("email", "alpha@test.com", "password", "password123"), null)
                .andExpect(status().isOk());
        t.postJson("/api/auth/login", Map.of("email", "alpha@test.com", "password", "wrong-password"), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorizationBoundaries() throws Exception {
        String player = t.register("Bravo Team", "bravo@test.com");
        String admin = t.login("admin@test.local", "AdminPassw0rd!");

        t.getReq("/api/player/dashboard", null).andExpect(status().isUnauthorized());
        t.getReq("/api/player/dashboard", "garbage.token.value").andExpect(status().isUnauthorized());
        t.getReq("/api/player/dashboard", player).andExpect(status().isOk());

        // player must not reach any admin API
        for (String url : List.of("/api/admin/dashboard", "/api/admin/teams", "/api/admin/round1", "/api/admin/rankings",
                "/api/admin/qualifications", "/api/admin/round2/submissions", "/api/admin/round2/submissions/1",
                "/api/admin/round1/questions", "/api/admin/round1/teams", "/api/admin/round2/challenge",
                "/api/admin/round2/results", "/api/admin/round2/reference-image")) {
            t.getReq(url, player).andExpect(status().isForbidden());
        }
        t.postJson("/api/admin/round2/evaluate", Map.of("submissionId", 1), player).andExpect(status().isForbidden());
        t.postJson("/api/admin/qualifications/finalize", Map.of(), player).andExpect(status().isForbidden());
        t.postJson("/api/admin/round1/publish", Map.of(), player).andExpect(status().isForbidden());
        t.postJson("/api/admin/round1/questions", Map.of("clue", "x", "answer", "ab"), player).andExpect(status().isForbidden());
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", 1, "questionNumbers", List.of(1)), player).andExpect(status().isForbidden());
        t.deleteReq("/api/admin/round1/questions/1", player).andExpect(status().isForbidden());
        t.upload("/api/admin/round2/reference-image", "x.png", new byte[20], player).andExpect(status().isForbidden());
        t.postJson("/api/admin/round2/publish", Map.of(), player).andExpect(status().isForbidden());

        // admin reaches admin APIs but not player APIs
        t.getReq("/api/admin/dashboard", admin).andExpect(status().isOk());
        t.getReq("/api/admin/teams", admin).andExpect(status().isOk());
        t.getReq("/api/player/dashboard", admin).andExpect(status().isForbidden());
        t.getReq("/api/round1/current", admin).andExpect(status().isForbidden());
    }

    @Test
    void dashboardShowsOnlyOwnTeam() throws Exception {
        String a = t.register("Own Team A", "owna@test.com");
        t.register("Own Team B", "ownb@test.com");
        JsonNode d = t.json(t.getReq("/api/player/dashboard", a));
        assertEquals("Own Team A", d.get("teamName").asText());
        assertEquals(2, d.get("members").size());
        assertEquals("ROUND_1", d.get("currentRound").asText());
        assertEquals("PENDING", d.get("qualificationStatus").asText());
        assertEquals("LOCKED", d.get("round2Status").asText());
    }

    @Test
    void questionOrderCoversEveryQuestionOnceAndDiffersBetweenTeams() {
        for (long team = 1; team <= 30; team++) {
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                seen.add(QuestionOrder.numberAt(team, i, 20));
            }
            assertEquals(20, seen.size(), "team " + team + " must see all 20 questions exactly once");
        }
        assertNotEquals(QuestionOrder.numberAt(1, 0, 20), QuestionOrder.numberAt(2, 0, 20));
        assertNotEquals(QuestionOrder.numberAt(1, 1, 20), QuestionOrder.numberAt(2, 1, 20));
    }

    @Test
    void round2IsLockedBeforeQualification() throws Exception {
        String token = t.register("Locked Team", "locked@test.com");
        t.getReq("/api/round2", token).andExpect(status().isForbidden());
        t.getReq("/api/round2/reference-image", token).andExpect(status().isForbidden());
        t.postJson("/api/round2/generate", Map.of("instruction", "x"), token).andExpect(status().isForbidden());
        t.postJson("/api/round2/submit", Map.of("notes", "n"), token).andExpect(status().isForbidden());
    }
}
