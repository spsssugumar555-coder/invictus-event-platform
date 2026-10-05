package com.invictus.platform.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GeminiRetryTest {
    static GeminiException http(int status) {
        return GeminiException.fromHttp(status, "{\"error\":{\"message\":\"m\"}}", "KEY", -1);
    }

    @Test
    void defaultBackoffIsTwoFiveTenSeconds() {
        assertEquals(List.of(2000L, 5000L, 10000L), List.of(GeminiRetry.DEFAULT_BACKOFF_MS[0], GeminiRetry.DEFAULT_BACKOFF_MS[1], GeminiRetry.DEFAULT_BACKOFF_MS[2]));
    }

    @Test
    void neverMoreThanFourCallsForAnyTemporaryFailure() {
        for (int status : new int[] {503, 429}) {
            AtomicInteger calls = new AtomicInteger();
            assertThrows(GeminiException.class, () -> GeminiRetry.execute(() -> {
                calls.incrementAndGet();
                throw http(status);
            }, GeminiRetry.DEFAULT_BACKOFF_MS, ms -> { }, s -> { }));
            assertEquals(4, calls.get());
        }
    }

    @Test
    void permanentErrorsAreCalledExactlyOnce() {
        for (int status : new int[] {400, 401, 403, 404}) {
            AtomicInteger calls = new AtomicInteger();
            assertThrows(GeminiException.class, () -> GeminiRetry.execute(() -> {
                calls.incrementAndGet();
                throw http(status);
            }, GeminiRetry.DEFAULT_BACKOFF_MS, ms -> { }, s -> { }));
            assertEquals(1, calls.get(), "status " + status);
        }
    }

    @Test
    void manyTeamsRetryIndependentlyWithoutSharedState() throws Exception {
        int teams = 12;
        ExecutorService pool = Executors.newFixedThreadPool(teams);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        List<AtomicInteger> counters = new ArrayList<>();
        for (int t = 0; t < teams; t++) {
            final int team = t;
            AtomicInteger calls = new AtomicInteger();
            counters.add(calls);
            // teams 0-3 succeed at once, 4-7 recover after one 503, 8-9 recover after 429 then 503, 10-11 never recover
            int failures = team < 4 ? 0 : team < 8 ? 1 : team < 10 ? 2 : 99;
            results.add(pool.submit(() -> {
                start.await();
                List<Long> slept = Collections.synchronizedList(new ArrayList<>());
                try {
                    return GeminiRetry.execute(() -> {
                        if (calls.getAndIncrement() < failures) throw http(team >= 8 && team < 10 && calls.get() == 1 ? 429 : 503);
                        return "result-" + team;
                    }, GeminiRetry.DEFAULT_BACKOFF_MS, slept::add, s -> { });
                } catch (GeminiException e) {
                    return "failed-" + team;
                }
            }));
        }
        start.countDown();
        for (int t = 0; t < teams; t++) {
            String r = results.get(t).get(10, TimeUnit.SECONDS);
            assertEquals(t >= 10 ? "failed-" + t : "result-" + t, r);
            assertEquals(t < 4 ? 1 : t < 8 ? 2 : t < 10 ? 3 : 4, counters.get(t).get(), "calls for team " + t);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }
}
