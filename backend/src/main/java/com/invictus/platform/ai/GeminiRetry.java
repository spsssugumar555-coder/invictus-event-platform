package com.invictus.platform.ai;

import java.util.function.Consumer;

/**
 * Bounded retry with backoff for temporary Gemini failures (HTTP 503 and 429 only).
 *
 * Stateless: every call keeps its own attempt counter on the stack, so concurrent requests from different
 * teams never share retry state and one team's failures cannot affect another's.
 */
final class GeminiRetry {
    private GeminiRetry() {}

    /** The default waits before retry 1, 2 and 3. At most 3 retries (4 attempts in total) are ever made. */
    static final long[] DEFAULT_BACKOFF_MS = {2_000, 5_000, 10_000};

    private static final long MAX_RETRY_AFTER_MS = 15_000;

    interface Call<T> {
        T run();
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    static <T> T execute(Call<T> call, long[] backoffMs, Sleeper sleeper, Consumer<String> log) {
        int totalAttempts = backoffMs.length + 1;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.run();
            } catch (GeminiException e) {
                log.accept("Gemini request failed: HTTP " + e.getStatus() + " " + e.getKind()
                        + " (attempt " + attempt + "/" + totalAttempts + "): " + e.getMessage());
                if (!e.isRetryable() || attempt >= totalAttempts) {
                    throw e;
                }
                long delay = backoffMs[attempt - 1];
                if (e.getRetryAfterMs() > 0) {
                    delay = Math.min(e.getRetryAfterMs(), MAX_RETRY_AFTER_MS);
                }
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new GeminiException(GeminiException.Kind.UNAVAILABLE, 0, "Interrupted while waiting to retry Gemini");
                }
            }
        }
    }
}
