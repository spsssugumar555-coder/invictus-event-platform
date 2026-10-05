package com.invictus.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invictus.platform.ai.AiClient;
import com.invictus.platform.repository.Round1AttemptRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Admin team management: create / edit / delete / list, authorization, and that Round 1 + Round 2 stay intact. */
@SpringBootTest(properties = "test.isolated=teams")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminTeamManagementTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired Round1AttemptRepository attempts;
    @MockBean AiClient ai;

    TestSupport t;
    String admin;

    @BeforeEach
    void setUp() throws Exception {
        t = new TestSupport(mvc, om, attempts);
        admin = t.adminToken();
    }

    static Map<String, Object> newTeam(String name, String email) {
        return Map.of("teamName", name, "captainName", "Captain " + name, "captainEmail", email,
                "members", List.of("Member A", "Member B"), "password", "password123");
    }

    JsonNode createTeam(String name, String email) throws Exception {
        return t.json(t.postJson("/api/admin/teams", newTeam(name, email), admin).andExpect(status().isCreated()));
    }

    JsonNode findTeam(String name) throws Exception {
        for (JsonNode row : t.json(t.getReq("/api/admin/teams", admin))) {
            if (row.get("name").asText().equals(name)) return row;
        }
        return null;
    }

    int totalTeams() throws Exception {
        return t.json(t.getReq("/api/admin/dashboard", admin)).get("totalTeams").asInt();
    }

    @Test
    void participantsAndAnonymousUsersCannotUseTeamManagementApis() throws Exception {
        String player = t.register("Plain Player", "plainplayer@test.com");
        long ownId = findTeam("Plain Player").get("id").asLong();

        t.getReq("/api/admin/teams", player).andExpect(status().isForbidden());
        t.postJson("/api/admin/teams", newTeam("Sneaky", "sneaky@test.com"), player).andExpect(status().isForbidden());
        t.putJson("/api/admin/teams/" + ownId, Map.of("teamName", "Renamed", "captainName", "C", "captainEmail", "plainplayer@test.com"), player)
                .andExpect(status().isForbidden());
        t.deleteReq("/api/admin/teams/" + ownId, player).andExpect(status().isForbidden());

        t.getReq("/api/admin/teams", null).andExpect(status().isUnauthorized());
        t.postJson("/api/admin/teams", newTeam("Anon", "anon@test.com"), null).andExpect(status().isUnauthorized());
        t.deleteReq("/api/admin/teams/" + ownId, null).andExpect(status().isUnauthorized());

        // nothing changed
        assertEquals("Plain Player", findTeam("Plain Player").get("name").asText());
        assertEquals(null, findTeam("Sneaky"));
        // and the player's own access is unaffected
        t.getReq("/api/player/dashboard", player).andExpect(status().isOk());
    }

    @Test
    void adminCreatesAUsableTeamThatIsNotQualifiedAndHasNoProgress() throws Exception {
        int before = totalTeams();
        JsonNode created = createTeam("Created Team", "created@test.com");
        assertEquals("Created Team", created.get("name").asText());
        assertEquals("PENDING", created.get("qualification").asText());
        assertEquals("NOT_STARTED", created.get("round1Status").asText());
        assertEquals("LOCKED", created.get("round2Status").asText());
        assertTrue(created.get("score").isNull());
        assertEquals(2, created.get("members").size());
        assertEquals(before + 1, totalTeams());
        assertEquals("Created Team", findTeam("Created Team").get("name").asText());

        // the new team can sign in with the credentials the organizer set, and is not qualified for Round 2
        String token = t.login("created@test.com", "password123");
        assertEquals("Created Team", t.json(t.getReq("/api/player/dashboard", token)).get("teamName").asText());
        t.getReq("/api/round2", token).andExpect(status().isForbidden());
        // no session exists until the team itself opens Round 1
        for (JsonNode r : t.json(t.getReq("/api/admin/round1", admin))) {
            assertFalse(r.get("teamName").asText().equals("Created Team"));
        }
    }

    @Test
    void createRejectsDuplicatesAndInvalidInput() throws Exception {
        createTeam("Unique Team", "unique@test.com");
        t.postJson("/api/admin/teams", newTeam("unique team", "other1@test.com"), admin).andExpect(status().isConflict());
        t.postJson("/api/admin/teams", newTeam("Another", "UNIQUE@test.com"), admin).andExpect(status().isConflict());
        t.postJson("/api/admin/teams", newTeam("Bad Mail", "not-an-email"), admin).andExpect(status().isBadRequest());
        Map<String, Object> shortPw = new HashMap<>(newTeam("Short Pw", "shortpw@test.com"));
        shortPw.put("password", "short");
        t.postJson("/api/admin/teams", shortPw, admin).andExpect(status().isBadRequest());
        Map<String, Object> noName = new HashMap<>(newTeam("x", "noname@test.com"));
        noName.put("teamName", " ");
        t.postJson("/api/admin/teams", noName, admin).andExpect(status().isBadRequest());
        Map<String, Object> tooMany = new HashMap<>(newTeam("Too Many", "toomany@test.com"));
        tooMany.put("members", List.of("a", "b", "c", "d", "e"));
        t.postJson("/api/admin/teams", tooMany, admin).andExpect(status().isBadRequest());
        assertEquals(null, findTeam("Bad Mail"));
        assertEquals(null, findTeam("Another"));
    }

    @Test
    void editChangesIdentityButKeepsRound1ProgressAndSequence() throws Exception {
        t.ensurePublished(admin);
        JsonNode team = createTeam("Edit Me", "editme@test.com");
        long id = team.get("id").asLong();
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", id, "questionNumbers", List.of(2, 1)), admin).andExpect(status().isOk());

        String token = t.login("editme@test.com", "password123");
        JsonNode first = t.json(t.getReq("/api/round1/current", token));
        long firstAttempt = first.at("/question/attemptId").asLong();
        JsonNode afterCorrect = t.submit(token, firstAttempt, t.realAnswer(firstAttempt));
        long activeAttempt = afterCorrect.at("/question/attemptId").asLong();
        assertEquals(10, afterCorrect.at("/summary/score").asInt());

        // edit everything except the password
        Map<String, Object> edit = new HashMap<>();
        edit.put("teamName", "Edited Name");
        edit.put("captainName", "New Captain");
        edit.put("captainEmail", "edited@test.com");
        edit.put("members", List.of("Only One"));
        JsonNode updated = t.json(t.putJson("/api/admin/teams/" + id, edit, admin).andExpect(status().isOk()));
        assertEquals("Edited Name", updated.get("name").asText());
        assertEquals("New Captain", updated.get("captainName").asText());
        assertEquals("edited@test.com", updated.get("captainEmail").asText());
        assertEquals(1, updated.get("members").size());
        // progress state is exactly as before
        assertEquals("IN_PROGRESS", updated.get("round1Status").asText());
        assertEquals(10, updated.get("score").asInt());
        assertEquals("PENDING", updated.get("qualification").asText());
        assertEquals(null, findTeam("Edit Me"));

        // login moved to the new email with the same password; the old email is gone
        t.postJson("/api/auth/login", Map.of("email", "editme@test.com", "password", "password123"), null).andExpect(status().isUnauthorized());
        String newToken = t.login("edited@test.com", "password123");
        JsonNode cur = t.json(t.getReq("/api/round1/current", newToken));
        assertEquals(activeAttempt, cur.at("/question/attemptId").asLong(), "the active question and its timer were not reset");
        assertEquals(10, cur.at("/summary/score").asInt());
        assertEquals(1, cur.at("/summary/completed").asInt());
        // the already-issued token still works too (it is bound to the team, not the email)
        t.getReq("/api/round1/current", token).andExpect(status().isOk());

        // optional password reset
        Map<String, Object> reset = new HashMap<>(edit);
        reset.put("password", "brandnewpass1");
        t.putJson("/api/admin/teams/" + id, reset, admin).andExpect(status().isOk());
        t.postJson("/api/auth/login", Map.of("email", "edited@test.com", "password", "password123"), null).andExpect(status().isUnauthorized());
        t.login("edited@test.com", "brandnewpass1");
        // same name in a different case, own email: allowed
        Map<String, Object> recase = new HashMap<>(edit);
        recase.put("teamName", "EDITED NAME");
        t.putJson("/api/admin/teams/" + id, recase, admin).andExpect(status().isOk());
    }

    @Test
    void editRejectsDuplicatesMissingTeamsAndBadInput() throws Exception {
        createTeam("Dup One", "dup1@test.com");
        long two = createTeam("Dup Two", "dup2@test.com").get("id").asLong();
        Map<String, Object> toOne = new HashMap<>(Map.of("teamName", "dup one", "captainName", "C", "captainEmail", "dup2@test.com"));
        t.putJson("/api/admin/teams/" + two, toOne, admin).andExpect(status().isConflict());
        Map<String, Object> emailClash = new HashMap<>(Map.of("teamName", "Dup Two", "captainName", "C", "captainEmail", "dup1@test.com"));
        t.putJson("/api/admin/teams/" + two, emailClash, admin).andExpect(status().isConflict());
        t.putJson("/api/admin/teams/999999", Map.of("teamName", "X", "captainName", "C", "captainEmail", "x@test.com"), admin).andExpect(status().isNotFound());
        t.putJson("/api/admin/teams/" + two, Map.of("teamName", "Dup Two", "captainName", "C", "captainEmail", "bad"), admin).andExpect(status().isBadRequest());
        t.putJson("/api/admin/teams/" + two, Map.of("teamName", "Dup Two", "captainName", "C", "captainEmail", "dup2@test.com", "password", "short"), admin)
                .andExpect(status().isBadRequest());
        assertEquals("Dup Two", findTeam("Dup Two").get("name").asText()); // nothing was half-applied
    }

    @Test
    void deleteRemovesTheTeamAndItsRound1RecordsWithoutTouchingOtherTeams() throws Exception {
        t.ensurePublished(admin);
        long a = createTeam("Delete Me", "deleteme@test.com").get("id").asLong();
        long b = createTeam("Keep Me", "keepme@test.com").get("id").asLong();
        t.postJson("/api/admin/round1/team-sequence", Map.of("teamId", a, "questionNumbers", List.of(3, 1)), admin).andExpect(status().isOk());

        String tokenA = t.login("deleteme@test.com", "password123");
        String tokenB = t.login("keepme@test.com", "password123");
        // both teams have a session, attempts and a queue; A also has a configured sequence
        JsonNode stateA = t.json(t.getReq("/api/round1/current", tokenA));
        t.submit(tokenA, stateA.at("/question/attemptId").asLong(), t.realAnswer(stateA.at("/question/attemptId").asLong()));
        JsonNode stateB = t.json(t.getReq("/api/round1/current", tokenB));
        long bAttempt = stateB.at("/question/attemptId").asLong();
        t.submit(tokenB, bAttempt, t.realAnswer(bAttempt));
        int before = totalTeams();

        t.deleteReq("/api/admin/teams/" + a, admin).andExpect(status().isNoContent());

        assertEquals(before - 1, totalTeams());
        assertEquals(null, findTeam("Delete Me"));
        for (JsonNode r : t.json(t.getReq("/api/admin/round1", admin))) assertFalse(r.get("teamName").asText().equals("Delete Me"));
        for (JsonNode r : t.json(t.getReq("/api/admin/round1/teams", admin))) assertFalse(r.get("teamName").asText().equals("Delete Me"));
        for (JsonNode r : t.json(t.getReq("/api/admin/rankings", admin))) assertFalse(r.get("teamName").asText().equals("Delete Me"));
        t.getReq("/api/admin/qualifications", admin).andExpect(status().isOk());
        t.getReq("/api/admin/round2/results", admin).andExpect(status().isOk());

        // the other team is untouched and still playing
        JsonNode keep = findTeam("Keep Me");
        assertEquals(10, keep.get("score").asInt());
        JsonNode stillB = t.json(t.getReq("/api/round1/current", tokenB));
        assertEquals(1, stillB.at("/summary/completed").asInt());
        assertEquals(10, stillB.at("/summary/score").asInt());
        assertFalse(stillB.at("/question").isNull());

        // the deleted team's credentials and token no longer work, and nothing crashes
        t.postJson("/api/auth/login", Map.of("email", "deleteme@test.com", "password", "password123"), null).andExpect(status().isUnauthorized());
        t.getReq("/api/player/dashboard", tokenA).andExpect(status().isNotFound());
        t.getReq("/api/round1/current", tokenA).andExpect(status().isNotFound());
        t.getReq("/api/round2", tokenA).andExpect(status().isForbidden());

        // deleting again is a clean 404, and the name/email can be reused for a brand new team with no history
        t.deleteReq("/api/admin/teams/" + a, admin).andExpect(status().isNotFound());
        JsonNode again = createTeam("Delete Me", "deleteme@test.com");
        assertEquals("NOT_STARTED", again.get("round1Status").asText());
        assertTrue(again.get("score").isNull());
    }

    @Test
    void authenticationStillWorksForAdminsAndPlayers() throws Exception {
        t.login(TestSupport.ADMIN_EMAIL, TestSupport.ADMIN_PASSWORD);
        String token = t.register("Auth Check", "authcheck@test.com");
        t.getReq("/api/player/dashboard", token).andExpect(status().isOk());
        t.getReq("/api/admin/dashboard", admin).andExpect(status().isOk());
        t.getReq("/api/admin/dashboard", token).andExpect(status().isForbidden());
    }
}
