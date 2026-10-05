package com.invictus.platform.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Exercises the real GeminiClient against a mock HTTP server: URL, model, key header, retries, error mapping. */
class GeminiClientTest {
    static final String KEY = "TEST-KEY-abcdefghijklmnopqrstuvwxyz";
    static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    static final String OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"<html>ok</html>\"}]},\"finishReason\":\"STOP\"}]}";
    static final String BUSY = "{\"error\":{\"code\":503,\"message\":\"This model is currently experiencing high demand.\",\"status\":\"UNAVAILABLE\"}}";

    MockRestServiceServer server;
    List<Long> sleeps;
    RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        sleeps = new ArrayList<>();
    }

    GeminiClient client(String model) {
        return client(model, 0);
    }

    GeminiClient client(String model, int maxOutputTokens) {
        return new GeminiClient(builder.build(), KEY, model, new long[] {2000, 5000, 10000}, sleeps::add, maxOutputTokens);
    }

    @Test
    void requestUsesTheConfiguredModelAndKeyHeaderAndNeverPutsTheKeyInTheBody() {
        server.expect(requestTo(BASE + "my-configured-model:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", KEY))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("hello"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("sys"))
                .andExpect(request -> assertFalse(((MockClientHttpRequest) request).getBodyAsString().contains(KEY)))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        GeminiClient c = client("my-configured-model");
        assertEquals("my-configured-model", c.model());
        assertEquals("<html>ok</html>", c.generate("sys", "hello", null, null, false));
        server.verify();
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void modelsPrefixIsAcceptedAndNotDoubled() {
        server.expect(requestTo(BASE + "abc-model:generateContent")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("models/abc-model").generate("s", "p", null, null, false);
        server.verify();
    }

    @Test
    void imageIsSentAsInlineData() {
        server.expect(requestTo(BASE + "m:generateContent"))
                .andExpect(jsonPath("$.contents[0].parts[1].inline_data.mime_type").value("image/png"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("m").generate("s", "p", new byte[] {1, 2, 3}, "image/png", false);
        server.verify();
    }

    @Test
    void retriesTwo503ThenSucceedsWithBackoff() {
        server.expect(ExpectedCount.times(2), requestTo(BASE + "m:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(BUSY));
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        assertEquals("<html>ok</html>", client("m").generate("s", "p", null, null, false));
        server.verify();
        assertEquals(List.of(2000L, 5000L), sleeps);
    }

    @Test
    void persistent503StopsAfterThreeRetriesWithUnavailable() {
        server.expect(ExpectedCount.times(4), requestTo(BASE + "m:generateContent"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body(BUSY));
        GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        server.verify(); // exactly 4 requests: 1 + 3 retries
        assertEquals(GeminiException.Kind.UNAVAILABLE, e.getKind());
        assertEquals(List.of(2000L, 5000L, 10000L), sleeps);
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void rateLimit429IsRetriedThenSucceeds() {
        server.expect(requestTo(BASE + "m:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"quota\"}}"));
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        assertEquals("<html>ok</html>", client("m").generate("s", "p", null, null, false));
        assertEquals(List.of(2000L), sleeps);
    }

    @Test
    void authErrorsAreNotRetried() {
        for (HttpStatus s : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN}) {
            setUp();
            server.expect(ExpectedCount.once(), requestTo(BASE + "m:generateContent"))
                    .andRespond(withStatus(s).contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"denied\"}}"));
            GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
            server.verify();
            assertEquals(GeminiException.Kind.AUTH, e.getKind());
            assertTrue(sleeps.isEmpty());
        }
    }

    @Test
    void invalidKeyReportedAs400IsAnAuthErrorAndTheKeyIsRedactedIfEchoed() {
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"API key not valid: " + KEY + "\"}}"));
        GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.AUTH, e.getKind());
        assertFalse(e.getMessage().contains(KEY));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void modelNotFoundIsASeparateErrorAndNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "nope:generateContent")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"models/nope is not found\"}}"));
        GeminiException e = assertThrows(GeminiException.class, () -> client("nope").generate("s", "p", null, null, false));
        server.verify();
        assertEquals(GeminiException.Kind.MODEL_NOT_FOUND, e.getKind());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void missingModelFailsClearlyWithoutCallingGoogle() {
        GeminiException e = assertThrows(GeminiException.class, () -> client("  ").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.NOT_CONFIGURED, e.getKind());
        server.verify(); // no request was made
    }

    @Test
    void truncatedAndBlockedResponsesAreClassified() {
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"<html\"}]},\"finishReason\":\"MAX_TOKENS\"}]}", MediaType.APPLICATION_JSON));
        assertEquals(GeminiException.Kind.TRUNCATED,
                assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false)).getKind());
        setUp();
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(
                "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", MediaType.APPLICATION_JSON));
        assertEquals(GeminiException.Kind.BLOCKED,
                assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false)).getKind());
    }

    @Test
    void maxOutputTokensIsNotSentUnlessConfigured() {
        server.expect(requestTo(BASE + "m:generateContent"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("m").generate("s", "p", null, null, false);
        server.verify();
        setUp();
        server.expect(requestTo(BASE + "m:generateContent"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(4096))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("m", 4096).generate("s", "p", null, null, false);
        server.verify();
    }

    @Test
    void jsonModeAsksForJsonAndPlainModeDoesNot() {
        server.expect(requestTo(BASE + "m:generateContent"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("m").generate("s", "p", null, null, true);
        setUp();
        server.expect(requestTo(BASE + "m:generateContent"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("m").generate("s", "p", null, null, false);
    }

    @Test
    void multiplePartsAreJoinedAndThoughtPartsAreIgnored() {
        String body = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"internal reasoning\",\"thought\":true},"
                + "{\"text\":\"<html>\"},{\"text\":\"page</html>\"}]},\"finishReason\":\"STOP\"}]}";
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertEquals("<html>page</html>", client("m").generate("s", "p", null, null, false));
    }

    @Test
    void emptyResponsesAreClassifiedAsEmptyWithSafeDiagnostics() {
        String[] bodies = {
                "{}",
                "{\"candidates\":[]}",
                "{\"candidates\":[{\"content\":{\"parts\":[]},\"finishReason\":\"STOP\"}]}",
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"   \"}]},\"finishReason\":\"STOP\"}]}"};
        for (String b : bodies) {
            setUp();
            server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(b, MediaType.APPLICATION_JSON));
            GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
            assertEquals(GeminiException.Kind.EMPTY, e.getKind(), b);
            assertFalse(e.getMessage().contains(KEY));
            assertTrue(sleeps.isEmpty(), "an empty answer is not retried");
        }
    }

    @Test
    void outOfTokensWithOnlyThinkingIsTruncatedAndReportsTokenCounts() {
        String body = "{\"candidates\":[{\"content\":{\"role\":\"model\"},\"finishReason\":\"MAX_TOKENS\"}],"
                + "\"usageMetadata\":{\"promptTokenCount\":900,\"thoughtsTokenCount\":31000}}";
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.TRUNCATED, e.getKind());
        assertTrue(e.getMessage().contains("thoughtTokens=31000") && e.getMessage().contains("finishReason=MAX_TOKENS"));
    }

    @Test
    void malformedSuccessBodyIsAClassifiedFailureAndNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "m:generateContent"))
                .andRespond(withSuccess("this is not json {", MediaType.APPLICATION_JSON));
        GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.OTHER, e.getKind());
        assertTrue(sleeps.isEmpty());
    }

    // ---- response shapes and request config -------------------------------------------------------------------

    @Test
    void noOutputCapIsSentUnlessConfigured() {
        server.expect(requestTo(BASE + "m:generateContent"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").doesNotExist())
                .andExpect(jsonPath("$.generationConfig.responseMimeType").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("m").generate("s", "p", null, null, false);
        server.verify();

        setUp();
        server.expect(requestTo(BASE + "m:generateContent"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(12345))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        client("m", 12345).generate("s", "p", null, null, true);
        server.verify();
    }

    @Test
    void multiPartResponsesAreJoinedAndThoughtPartsAreIgnored() {
        String body = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"internal reasoning\",\"thought\":true},"
                + "{\"text\":\"<html>\"},{\"text\":\"page</html>\"}]},\"finishReason\":\"STOP\"}]}";
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertEquals("<html>page</html>", client("m").generate("s", "p", null, null, false));
    }

    @Test
    void emptyResponsesAreClassifiedWithSafeDiagnostics() {
        // no candidates at all
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess("{\"usageMetadata\":{\"promptTokenCount\":10}}", MediaType.APPLICATION_JSON));
        GeminiException none = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.EMPTY, none.getKind());
        assertTrue(none.getMessage().contains("no candidates") && none.getMessage().contains("promptTokens=10"));

        // a candidate with no text parts (e.g. only reasoning, or content cut off)
        setUp();
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"role\":\"model\"},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"thoughtsTokenCount\":900}}", MediaType.APPLICATION_JSON));
        GeminiException noText = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.EMPTY, noText.getKind());
        assertTrue(noText.getMessage().contains("thoughtTokens=900"));
        assertFalse(noText.getMessage().contains(KEY));
    }

    @Test
    void maxTokensWithNoTextReportsTruncationDiagnostics() {
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"role\":\"model\"},\"finishReason\":\"MAX_TOKENS\"}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":2000,\"candidatesTokenCount\":0,\"thoughtsTokenCount\":31000}}", MediaType.APPLICATION_JSON));
        GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.TRUNCATED, e.getKind());
        assertTrue(e.getMessage().contains("finishReason=MAX_TOKENS") && e.getMessage().contains("thoughtTokens=31000"));
    }

    @Test
    void malformedSuccessBodyIsAClassifiedErrorNotARawException() {
        server.expect(requestTo(BASE + "m:generateContent")).andRespond(withSuccess("this is not json {{{", MediaType.APPLICATION_JSON));
        GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        assertEquals(GeminiException.Kind.OTHER, e.getKind());
        assertTrue(sleeps.isEmpty(), "unreadable responses are not retried");
    }

    @Test
    void plain400IsBadRequestAndNotRetried() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "m:generateContent")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"message\":\"maxOutputTokens must be <= 8192\"}}"));
        GeminiException e = assertThrows(GeminiException.class, () -> client("m").generate("s", "p", null, null, false));
        server.verify();
        assertEquals(GeminiException.Kind.BAD_REQUEST, e.getKind());
        assertTrue(e.getMessage().contains("maxOutputTokens must be <= 8192"), "the real reason is kept for the server log");
        assertTrue(sleeps.isEmpty());
    }
}
