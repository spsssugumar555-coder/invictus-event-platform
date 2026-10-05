package com.invictus.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;

/**
 * A classified Gemini failure. The message is safe to log and to show to admins: it never contains the API key.
 */
public class GeminiException extends RuntimeException {
    public enum Kind {
        /** HTTP 503 / 5xx / network: Gemini is busy or unreachable. Temporary. */
        UNAVAILABLE,
        /** HTTP 429. Temporary. */
        RATE_LIMITED,
        /** HTTP 401 / 403 / invalid key / missing key. Configuration problem, never retried. */
        AUTH,
        /** HTTP 404. The configured GEMINI_MODEL does not exist for this key. Configuration problem. */
        MODEL_NOT_FOUND,
        /** GEMINI_MODEL is not set. Configuration problem. */
        NOT_CONFIGURED,
        /** HTTP 400 or other 4xx. */
        BAD_REQUEST,
        /** The response was blocked by safety filters. */
        BLOCKED,
        /** The model stopped because it ran out of output tokens. */
        TRUNCATED,
        /** 200 with no usable content. */
        EMPTY,
        OTHER
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Kind kind;
    private final int status;
    private final long retryAfterMs;

    public GeminiException(Kind kind, int status, String message) {
        this(kind, status, message, -1);
    }

    public GeminiException(Kind kind, int status, String message, long retryAfterMs) {
        super(message);
        this.kind = kind;
        this.status = status;
        this.retryAfterMs = retryAfterMs;
    }

    public Kind getKind() {
        return kind;
    }

    /** HTTP status from Gemini, or 0 when there was none. */
    public int getStatus() {
        return status;
    }

    /** Value of Retry-After in milliseconds, or -1 when absent. */
    public long getRetryAfterMs() {
        return retryAfterMs;
    }

    /** Only 503 and 429 are retried. Auth, model-not-found and bad requests never are. */
    public boolean isRetryable() {
        return status == 503 || status == 429;
    }

    /** Builds a classified exception from a Gemini HTTP error response. */
    public static GeminiException fromHttp(int status, String body, String apiKey, long retryAfterMs) {
        String message = safeMessage(body, apiKey);       // redacted: the only text that is ever shown or logged
        String forClassifying = safeMessage(body, null);  // never stored or displayed
        Kind kind;
        if (status == 429) {
            kind = Kind.RATE_LIMITED;
        } else if (status == 503) {
            kind = Kind.UNAVAILABLE;
        } else if (status == 401 || status == 403) {
            kind = Kind.AUTH;
        } else if (status == 404) {
            kind = Kind.MODEL_NOT_FOUND;
        } else if (status == 400) {
            // Google reports an invalid API key as HTTP 400 "API key not valid"
            kind = forClassifying.toLowerCase(Locale.ROOT).contains("api key") ? Kind.AUTH : Kind.BAD_REQUEST;
        } else if (status >= 500) {
            kind = Kind.UNAVAILABLE;
        } else {
            kind = Kind.OTHER;
        }
        return new GeminiException(kind, status, "Gemini HTTP " + status + ": " + message, retryAfterMs);
    }

    /** Extracts error.message from Google's JSON error body, redacts the API key, and bounds the length. */
    static String safeMessage(String body, String apiKey) {
        String text = body == null ? "" : body;
        try {
            JsonNode n = MAPPER.readTree(text);
            JsonNode m = n.path("error").path("message");
            if (m.isTextual()) {
                text = m.asText();
            }
        } catch (Exception ignored) {
            // not JSON: fall through with the raw text
        }
        if (apiKey != null && !apiKey.isBlank()) {
            text = text.replace(apiKey, "[redacted]");
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            text = "no details";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }
}
