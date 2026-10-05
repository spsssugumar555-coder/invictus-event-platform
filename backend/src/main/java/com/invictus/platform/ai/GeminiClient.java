package com.invictus.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls Google Gemini server-side (Generative Language API v1beta, models/{model}:generateContent).
 * The key comes from GEMINI_API_KEY and the model from GEMINI_MODEL; neither is ever sent to the browser, and the
 * key is never logged. Temporary failures (503, 429) are retried a bounded number of times with backoff.
 *
 * This bean is a stateless singleton: all per-request data lives in local variables, so it is safe for many teams
 * to call it concurrently.
 */
@Component
public class GeminiClient implements AiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient rest;
    private final String apiKey;
    private final String model;
    private final long[] backoffMs;
    private final GeminiRetry.Sleeper sleeper;
    /** 0 = do not send maxOutputTokens, so the model uses its own limit. */
    private final int maxOutputTokens;

    @Autowired
    public GeminiClient(@Value("${app.gemini.api-key:}") String apiKey,
                        @Value("${app.gemini.model:}") String model,
                        @Value("${app.gemini.max-output-tokens:0}") int maxOutputTokens) {
        this(buildRest(), apiKey, model, GeminiRetry.DEFAULT_BACKOFF_MS, Thread::sleep, maxOutputTokens);
    }

    /** For tests: inject a RestClient bound to a mock server, short backoffs, and a recording sleeper. */
    GeminiClient(RestClient rest, String apiKey, String model, long[] backoffMs, GeminiRetry.Sleeper sleeper,
                 int maxOutputTokens) {
        this.rest = rest;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = normalizeModel(model);
        this.backoffMs = backoffMs;
        this.sleeper = sleeper;
        this.maxOutputTokens = maxOutputTokens;
    }

    private static RestClient buildRest() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(120_000);
        return RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").requestFactory(factory).build();
    }

    /** Accepts "my-model" or "models/my-model" and returns "my-model". */
    static String normalizeModel(String raw) {
        String m = raw == null ? "" : raw.trim();
        return m.startsWith("models/") ? m.substring("models/".length()) : m;
    }

    String model() {
        return model;
    }

    @Override
    public String generate(String system, String prompt, byte[] image, String imageMime, boolean json) {
        if (apiKey.isEmpty()) {
            throw new GeminiException(GeminiException.Kind.AUTH, 0, "GEMINI_API_KEY is not configured on the server");
        }
        if (model.isEmpty()) {
            throw new GeminiException(GeminiException.Kind.NOT_CONFIGURED, 0, "GEMINI_MODEL is not configured on the server");
        }
        Map<String, Object> body = buildBody(system, prompt, image, imageMime, json, maxOutputTokens);
        return GeminiRetry.execute(() -> post(body), backoffMs, sleeper, log::warn);
    }

    private static Map<String, Object> buildBody(String system, String prompt, byte[] image, String imageMime,
                                                 boolean json, int maxOutputTokens) {
        List<Object> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        if (image != null && image.length > 0) {
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", imageMime == null ? "image/png" : imageMime,
                    "data", Base64.getEncoder().encodeToString(image))));
        }
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", json ? 0.2 : 0.6);
        // Only sent when explicitly configured: thinking models count reasoning tokens against this limit and
        // different models have different maximums, so a fixed value can be rejected (HTTP 400) or cut the page off.
        if (maxOutputTokens > 0) {
            generationConfig.put("maxOutputTokens", maxOutputTokens);
        }
        if (json) {
            generationConfig.put("responseMimeType", "application/json");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", system))));
        body.put("contents", List.of(Map.of("role", "user", "parts", parts)));
        body.put("generationConfig", generationConfig);
        return body;
    }

    /** One HTTP attempt. Translates every failure into a classified GeminiException. */
    private String post(Map<String, Object> body) {
        try {
            JsonNode response = rest.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return extractText(response);
        } catch (RestClientResponseException e) {
            throw GeminiException.fromHttp(e.getStatusCode().value(), e.getResponseBodyAsString(), apiKey,
                    retryAfterMs(e.getResponseHeaders()));
        } catch (ResourceAccessException e) {
            throw new GeminiException(GeminiException.Kind.UNAVAILABLE, 0, "Could not reach Gemini (network error or timeout)");
        } catch (RestClientException e) {
            // e.g. a 200 whose body is not valid JSON
            throw new GeminiException(GeminiException.Kind.OTHER, 200, "Gemini returned a response that could not be read");
        }
    }

    private static long retryAfterMs(HttpHeaders headers) {
        String v = headers == null ? null : headers.getFirst("Retry-After");
        if (v == null) {
            return -1;
        }
        try {
            return Long.parseLong(v.trim()) * 1000;
        } catch (NumberFormatException e) {
            return -1; // HTTP-date form is ignored; the fixed backoff applies
        }
    }

    static String extractText(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            throw new GeminiException(GeminiException.Kind.EMPTY, 200, "Gemini returned an empty response");
        }
        String block = response.path("promptFeedback").path("blockReason").asText("");
        if (!block.isEmpty()) {
            throw new GeminiException(GeminiException.Kind.BLOCKED, 200, "Gemini blocked the request (" + block + ")");
        }
        JsonNode candidate = response.path("candidates").path(0);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.hasNonNull("text") && !part.path("thought").asBoolean(false)) {
                text.append(part.get("text").asText());
            }
        }
        String finish = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(finish)) {
            throw new GeminiException(GeminiException.Kind.TRUNCATED, 200,
                    "Gemini stopped because the output limit was reached (" + describe(response, candidate) + ")");
        }
        if (text.toString().isBlank()) {
            String why = candidate.isMissingNode() ? "no candidates" : "no text";
            throw new GeminiException(GeminiException.Kind.EMPTY, 200,
                    "Gemini returned " + why + " (" + describe(response, candidate) + ")");
        }
        return text.toString();
    }

    /** Safe diagnostics for the log: reasons and token counts only, never prompt or output content. */
    private static String describe(JsonNode response, JsonNode candidate) {
        JsonNode u = response.path("usageMetadata");
        return "finishReason=" + candidate.path("finishReason").asText("none")
                + ", parts=" + candidate.path("content").path("parts").size()
                + ", promptTokens=" + u.path("promptTokenCount").asInt(-1)
                + ", outputTokens=" + u.path("candidatesTokenCount").asInt(-1)
                + ", thoughtTokens=" + u.path("thoughtsTokenCount").asInt(-1);
    }
}
