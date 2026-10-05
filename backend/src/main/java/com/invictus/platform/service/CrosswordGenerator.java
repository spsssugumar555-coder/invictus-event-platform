package com.invictus.platform.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic crossword layout generator (no randomness: same words in, same layout out).
 *
 * Greedy placement: words are placed so each new word crosses already placed words, with the
 * standard rules (no side-by-side letters, no extending an existing word). Several starting words are
 * tried and the layout that places the most words, then has the smallest area, wins.
 */
public final class CrosswordGenerator {
    private CrosswordGenerator() {}

    public record Word(long id, String answer) {}

    /** row/col are 0-based after normalization; number is the printed clue number of the start cell. */
    public record Placement(long id, int row, int col, boolean across, int number, int length) {}

    public record Layout(int rows, int cols, List<Placement> placements, List<Long> unplaced) {}

    private static final int START_CANDIDATES = 40;

    public static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    public static Layout generate(List<Word> input) {
        List<Word> words = new ArrayList<>();
        for (Word w : input) {
            words.add(new Word(w.id(), normalize(w.answer())));
        }
        words.sort(Comparator.comparingInt((Word w) -> w.answer().length()).reversed().thenComparingLong(Word::id));

        Attempt best = null;
        int tries = Math.min(START_CANDIDATES, words.size());
        for (int k = 0; k < Math.max(tries, 1); k++) {
            List<Word> order = new ArrayList<>(words);
            if (!order.isEmpty()) {
                order.add(0, order.remove(k));
            }
            Attempt a = run(order);
            if (best == null || a.better(best)) {
                best = a;
            }
        }
        return best == null ? new Layout(0, 0, List.of(), List.of()) : best.toLayout();
    }

    // ------------------------------------------------------------------ internals

    private static long key(int r, int c) {
        return ((long) r << 32) ^ (c & 0xffffffffL);
    }

    private static final class Raw {
        final long id;
        final String text;
        final int row;
        final int col;
        final boolean across;

        Raw(long id, String text, int row, int col, boolean across) {
            this.id = id;
            this.text = text;
            this.row = row;
            this.col = col;
            this.across = across;
        }
    }

    private static final class Attempt {
        final Map<Long, Character> letters = new HashMap<>();
        final Set<Long> acrossCells = new HashSet<>();
        final Set<Long> downCells = new HashSet<>();
        final List<Raw> placed = new ArrayList<>();
        final List<Long> unplaced = new ArrayList<>();

        int minR, maxR, minC, maxC;

        int area() {
            return placed.isEmpty() ? 0 : (maxR - minR + 1) * (maxC - minC + 1);
        }

        boolean better(Attempt o) {
            if (unplaced.size() != o.unplaced.size()) return unplaced.size() < o.unplaced.size();
            return area() < o.area();
        }

        boolean empty(int r, int c) {
            return !letters.containsKey(key(r, c));
        }

        /** Returns number of crossings if the word fits, or -1 when invalid. */
        int fit(String w, int r, int c, boolean across) {
            int dr = across ? 0 : 1;
            int dc = across ? 1 : 0;
            if (!empty(r - dr, c - dc) || !empty(r + dr * w.length(), c + dc * w.length())) return -1;
            int crossings = 0;
            for (int i = 0; i < w.length(); i++) {
                int rr = r + dr * i;
                int cc = c + dc * i;
                Character existing = letters.get(key(rr, cc));
                if (existing != null) {
                    if (existing != w.charAt(i)) return -1;
                    Set<Long> same = across ? acrossCells : downCells;
                    if (same.contains(key(rr, cc))) return -1; // would overlap a word running the same way
                    crossings++;
                } else {
                    // side neighbours must be empty so no accidental two-letter words appear
                    if (!empty(rr + dc, cc + dr) || !empty(rr - dc, cc - dr)) return -1;
                }
            }
            return crossings;
        }

        void put(long id, String w, int r, int c, boolean across) {
            int dr = across ? 0 : 1;
            int dc = across ? 1 : 0;
            for (int i = 0; i < w.length(); i++) {
                int rr = r + dr * i;
                int cc = c + dc * i;
                letters.put(key(rr, cc), w.charAt(i));
                (across ? acrossCells : downCells).add(key(rr, cc));
            }
            int endR = r + dr * (w.length() - 1);
            int endC = c + dc * (w.length() - 1);
            if (placed.isEmpty()) {
                minR = r; maxR = endR; minC = c; maxC = endC;
            } else {
                minR = Math.min(minR, r); maxR = Math.max(maxR, endR);
                minC = Math.min(minC, c); maxC = Math.max(maxC, endC);
            }
            placed.add(new Raw(id, w, r, c, across));
        }

        Layout toLayout() {
            if (placed.isEmpty()) return new Layout(0, 0, List.of(), unplaced);
            int rows = maxR - minR + 1;
            int cols = maxC - minC + 1;
            // clue numbers: scan row-major, number every cell that starts a word
            Map<Long, Integer> numbers = new HashMap<>();
            Set<Long> starts = new HashSet<>();
            for (Raw p : placed) starts.add(key(p.row, p.col));
            int n = 1;
            for (int r = minR; r <= maxR; r++) {
                for (int c = minC; c <= maxC; c++) {
                    if (starts.contains(key(r, c))) numbers.put(key(r, c), n++);
                }
            }
            List<Placement> out = new ArrayList<>();
            for (Raw p : placed) {
                out.add(new Placement(p.id, p.row - minR, p.col - minC, p.across, numbers.get(key(p.row, p.col)), p.text.length()));
            }
            out.sort(Comparator.comparingLong(Placement::id));
            return new Layout(rows, cols, out, unplaced);
        }
    }

    private static Attempt run(List<Word> order) {
        Attempt a = new Attempt();
        List<Word> pending = new ArrayList<>(order);
        if (pending.isEmpty()) return a;
        Word first = pending.remove(0);
        a.put(first.id(), first.answer(), 0, 0, true);

        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            List<Word> still = new ArrayList<>();
            for (Word w : pending) {
                if (!tryPlace(a, w)) still.add(w);
                else progress = true;
            }
            pending = still;
        }
        for (Word w : pending) a.unplaced.add(w.id());
        return a;
    }

    private static boolean tryPlace(Attempt a, Word w) {
        String text = w.answer();
        if (text.isEmpty()) return false;
        // deterministic iteration over existing cells
        TreeMap<Long, Character> cells = new TreeMap<>(a.letters);
        int bestScore = -1;
        int bestArea = Integer.MAX_VALUE;
        int bestR = 0;
        int bestC = 0;
        boolean bestAcross = true;
        Set<String> seen = new HashSet<>();
        for (Map.Entry<Long, Character> e : cells.entrySet()) {
            int er = (int) (e.getKey() >> 32);
            int ec = (int) (long) e.getKey();
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) != e.getValue()) continue;
                for (boolean across : new boolean[] {true, false}) {
                    int r = across ? er : er - i;
                    int c = across ? ec - i : ec;
                    if (!seen.add(r + ":" + c + ":" + across)) continue;
                    int crossings = a.fit(text, r, c, across);
                    if (crossings < 1) continue;
                    int endR = r + (across ? 0 : text.length() - 1);
                    int endC = c + (across ? text.length() - 1 : 0);
                    int area = (Math.max(a.maxR, endR) - Math.min(a.minR, r) + 1) * (Math.max(a.maxC, endC) - Math.min(a.minC, c) + 1);
                    boolean better = crossings > bestScore
                            || (crossings == bestScore && area < bestArea)
                            || (crossings == bestScore && area == bestArea && (r < bestR || (r == bestR && c < bestC)));
                    if (better) {
                        bestScore = crossings; bestArea = area; bestR = r; bestC = c; bestAcross = across;
                    }
                }
            }
        }
        if (bestScore < 1) return false;
        a.put(w.id(), text, bestR, bestC, bestAcross);
        return true;
    }
}
