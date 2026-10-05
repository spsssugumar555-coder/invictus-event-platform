package com.invictus.platform.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic per-team question order. Each team gets its own start offset and a stride that is
 * coprime with the number of questions, so every team visits every question exactly once, in a
 * different order. Example for 20 questions: team 1 -> 11, 4, 17 ... ; team 2 -> 18, 5, 12 ...
 */
public final class QuestionOrder {
    private static final int[] STRIDES = {3, 7, 9, 11, 13, 17, 19, 21, 23};

    private QuestionOrder() {}

    /** Returns the 1-based question number a team gets at the given 0-based position. */
    public static int numberAt(long teamId, int index, int total) {
        int start = (int) ((teamId * 7 + 3) % total);
        int stride = strideFor(teamId, total);
        return (int) (((long) start + (long) index * stride) % total) + 1;
    }

    static int strideFor(long teamId, int total) {
        List<Integer> usable = new ArrayList<>();
        for (int s : STRIDES) {
            if (gcd(s, total) == 1) {
                usable.add(s);
            }
        }
        if (usable.isEmpty()) {
            usable.add(1);
        }
        return usable.get((int) (teamId % usable.size()));
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
