package com.invictus.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invictus.platform.ai.GeminiException;
import com.invictus.platform.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Pure unit tests: how model output is turned into a page, and how failures are worded for players. */
class Round2HelpersTest {
    static final String DOC = "<!DOCTYPE html><html><body><h1>Hi</h1></body></html>";

    @Test
    void acceptsBareFencedAndChattyDocuments() {
        assertEquals(DOC, HtmlExtractor.extract(DOC));
        assertEquals(DOC, HtmlExtractor.extract("```html\n" + DOC + "\n```"));
        assertEquals(DOC, HtmlExtractor.extract("Sure! Here is your page:\n```html\n" + DOC + "\n```\nLet me know if you want changes."));
    }

    @Test
    void wrapsABareFragmentIntoAFullResponsiveDocument() {
        String out = HtmlExtractor.extract("```html\n<div class=\"x\">Hello</div>\n```");
        assertTrue(out.startsWith("<!DOCTYPE html>") && out.contains("viewport") && out.contains("<div class=\"x\">Hello</div>"));
        assertFalse(out.contains("```"));
    }

    @Test
    void rejectsProseJsonEmptyAndNull() {
        assertNull(HtmlExtractor.extract("I am sorry, I cannot help with that."));
        assertNull(HtmlExtractor.extract("{\"error\":\"no\"}"));
        assertNull(HtmlExtractor.extract("  "));
        assertNull(HtmlExtractor.extract(null));
    }

    @Test
    void serviceTurnsUnusableOutputIntoACleanError() {
        ApiException e = assertThrows(ApiException.class, () -> Round2Service.extractHtml("no markup here"));
        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
        assertEquals(DOC, Round2Service.extractHtml("```html\n" + DOC + "\n```"));
    }

    @Test
    void geminiFailuresMapToCleanPlayerMessages() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, Round2Service.toApiException(new GeminiException(GeminiException.Kind.UNAVAILABLE, 503, "x")).getStatus());
        assertEquals("Gemini is temporarily unavailable. Please try again shortly.",
                Round2Service.toApiException(new GeminiException(GeminiException.Kind.RATE_LIMITED, 429, "x")).getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, Round2Service.toApiException(new GeminiException(GeminiException.Kind.AUTH, 403, "secret detail")).getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, Round2Service.toApiException(new GeminiException(GeminiException.Kind.MODEL_NOT_FOUND, 404, "x")).getStatus());
        ApiException truncated = Round2Service.toApiException(new GeminiException(GeminiException.Kind.TRUNCATED, 200, "finishReason=MAX_TOKENS"));
        assertTrue(truncated.getMessage().contains("simpler page"));
        for (GeminiException.Kind k : GeminiException.Kind.values()) {
            String msg = Round2Service.toApiException(new GeminiException(k, 500, "RAW-INTERNAL-DETAIL finishReason=x")).getMessage();
            assertFalse(msg.contains("RAW-INTERNAL-DETAIL") || msg.contains("Exception"), "raw detail leaked for " + k);
        }
    }
}
