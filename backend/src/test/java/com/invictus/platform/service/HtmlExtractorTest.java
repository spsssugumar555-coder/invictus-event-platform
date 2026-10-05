package com.invictus.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invictus.platform.ai.GeminiException;
import com.invictus.platform.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class HtmlExtractorTest {
    static final String DOC = "<!DOCTYPE html><html><body><h1>Hi</h1></body></html>";

    @Test
    void acceptsBareFencedAndChattyOutput() {
        assertEquals(DOC, HtmlExtractor.extract(DOC));
        assertEquals(DOC, HtmlExtractor.extract("```html\n" + DOC + "\n```"));
        assertEquals(DOC, HtmlExtractor.extract("Sure! Here you go:\n```html\n" + DOC + "\n```\nHope this helps!"));
        assertEquals("<html><body>x</body></html>", HtmlExtractor.extract("<html><body>x</body></html>"));
    }

    @Test
    void keepsAnUnfinishedDocumentButDropsTheFence() {
        assertEquals("<!DOCTYPE html><html><body><p>half", HtmlExtractor.extract("```html\n<!DOCTYPE html><html><body><p>half"));
    }

    @Test
    void wrapsABodyFragmentInAResponsiveDocument() {
        String out = HtmlExtractor.extract("```html\n<div class=\"hero\">Hello</div>\n```");
        assertTrue(out.startsWith("<!DOCTYPE html>") && out.contains("viewport") && out.contains("<div class=\"hero\">Hello</div>"));
        assertFalse(out.contains("```"));
    }

    @Test
    void rejectsProseJsonEmptyAndNull() {
        assertNull(HtmlExtractor.extract("I cannot help with that."));
        assertNull(HtmlExtractor.extract("{\"error\":\"x\"}"));
        assertNull(HtmlExtractor.extract("   "));
        assertNull(HtmlExtractor.extract(null));
    }

    @Test
    void unusableOutputBecomesACleanApiErrorNeverARawException() {
        ApiException e = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> Round2Service.extractHtml("no markup here"));
        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
        assertEquals("The AI did not return a web page. Please try again.", e.getMessage());
    }

    @Test
    void everyGeminiFailureKindMapsToACleanUserMessage() {
        for (GeminiException.Kind k : GeminiException.Kind.values()) {
            ApiException e = Round2Service.toApiException(new GeminiException(k, 0, "Gemini HTTP 000: internal detail SECRET-DETAIL"));
            assertFalse(e.getMessage().contains("SECRET-DETAIL") || e.getMessage().contains("Gemini HTTP"), "raw detail leaked for " + k);
        }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, Round2Service.toApiException(new GeminiException(GeminiException.Kind.UNAVAILABLE, 503, "x")).getStatus());
        assertEquals("Gemini is temporarily unavailable. Please try again shortly.",
                Round2Service.toApiException(new GeminiException(GeminiException.Kind.RATE_LIMITED, 429, "x")).getMessage());
        assertTrue(Round2Service.toApiException(new GeminiException(GeminiException.Kind.TRUNCATED, 200, "x")).getMessage().contains("too large"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, Round2Service.toApiException(new GeminiException(GeminiException.Kind.MODEL_NOT_FOUND, 404, "x")).getStatus());
    }
}
