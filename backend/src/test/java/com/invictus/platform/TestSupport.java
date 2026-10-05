package com.invictus.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invictus.platform.repository.Round1AttemptRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Small helper wrapper around MockMvc shared by the test classes. */
class TestSupport {
    static final String ADMIN_EMAIL = "admin@test.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";

    /** Clues deliberately do not contain their own answers. */
    static final String[][] WORDS = {
            {"Brewed in Indonesia and runs on a virtual machine", "JAVA"},
            {"Language that styles web pages", "CSS"},
            {"Structure markup for pages", "HTML"},
            {"Query language for relational data", "SQL"},
            {"Version control tool by Linus", "GIT"},
            {"Last in first out structure", "STACK"}};

    final MockMvc mvc;
    final ObjectMapper om;
    final Round1AttemptRepository attempts;

    TestSupport(MockMvc mvc, ObjectMapper om, Round1AttemptRepository attempts) {
        this.mvc = mvc;
        this.om = om;
        this.attempts = attempts;
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }

    ResultActions postJson(String url, Object body, String token) throws Exception {
        return mvc.perform(auth(post(url).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)), token));
    }

    ResultActions putJson(String url, Object body, String token) throws Exception {
        return mvc.perform(auth(put(url).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)), token));
    }

    ResultActions getReq(String url, String token) throws Exception {
        return mvc.perform(auth(get(url), token));
    }

    ResultActions deleteReq(String url, String token) throws Exception {
        return mvc.perform(auth(delete(url), token));
    }

    ResultActions upload(String url, String filename, byte[] bytes, String token) throws Exception {
        return mvc.perform(auth(multipart(url).file(new MockMultipartFile("file", filename, "image/png", bytes)), token));
    }

    String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    JsonNode json(ResultActions r) throws Exception {
        return om.readTree(body(r));
    }

    String register(String team, String email) throws Exception {
        Map<String, Object> body = Map.of("teamName", team, "captainName", "Captain", "captainEmail", email,
                "members", List.of("Member One", "Member Two"), "password", "password123");
        MvcResult res = postJson("/api/auth/register", body, null).andReturn();
        if (res.getResponse().getStatus() != 201) {
            throw new AssertionError("register failed: " + res.getResponse().getContentAsString());
        }
        return om.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    String login(String email, String password) throws Exception {
        MvcResult res = postJson("/api/auth/login", Map.of("email", email, "password", password), null).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /** Idempotent: replaces the sample questions with WORDS and publishes the crossword (admin API only). */
    void ensurePublished(String admin) throws Exception {
        JsonNode set = json(getReq("/api/admin/round1/questions", admin));
        if (set.get("published").asBoolean()) {
            return;
        }
        for (JsonNode q : set.get("questions")) {
            deleteReq("/api/admin/round1/questions/" + q.get("id").asLong(), admin).andExpect(status().isNoContent());
        }
        for (String[] w : WORDS) {
            postJson("/api/admin/round1/questions", Map.of("clue", w[0], "answer", w[1]), admin).andExpect(status().isCreated());
        }
        postJson("/api/admin/round1/publish", Map.of(), admin).andExpect(status().isOk());
    }

    String realAnswer(long attemptId) {
        return attempts.findById(attemptId).orElseThrow().question.answer;
    }

    long questionIdOf(long attemptId) {
        return attempts.findById(attemptId).orElseThrow().question.id;
    }

    JsonNode submit(String token, long attemptId, String answer) throws Exception {
        return json(postJson("/api/round1/answer", Map.of("attemptId", attemptId, "answer", answer), token));
    }

    /** Fetches the current question, looks up the real answer in the DB (test-only), and submits it. */
    JsonNode answerCurrent(String token, boolean correct) throws Exception {
        JsonNode cur = json(getReq("/api/round1/current", token));
        long attemptId = cur.at("/question/attemptId").asLong();
        return submit(token, attemptId, correct ? realAnswer(attemptId) : "ZZZWRONG");
    }

    /** Answers every question correctly until the team's Round 1 is COMPLETED. */
    void completeRound1(String token) throws Exception {
        JsonNode state = json(getReq("/api/round1/current", token));
        for (int i = 0; i < 100 && !state.at("/question").isNull(); i++) {
            long id = state.at("/question/attemptId").asLong();
            state = submit(token, id, realAnswer(id));
        }
    }
}
