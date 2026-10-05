package com.invictus.platform.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the web page out of whatever the model returned: a bare document, a markdown-fenced block, a document
 * surrounded by chatty text, or (as a last resort) a bare HTML fragment that is wrapped into a full document.
 * Pure function, no state.
 */
public final class HtmlExtractor {
    private HtmlExtractor() {}

    private static final Pattern FRAGMENT = Pattern.compile("<(body|main|div|section|header|nav|footer|article|style|h1|h2|ul|form)\\b", Pattern.CASE_INSENSITIVE);

    private static final String PREFIX = "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Preview</title></head><body>";

    /** @return the HTML document, or null when the text contains no usable markup. */
    public static String extract(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("<!doctype");
        if (start < 0) {
            start = lower.indexOf("<html");
        }
        if (start >= 0) {
            String doc = t.substring(start);
            int end = doc.toLowerCase(Locale.ROOT).lastIndexOf("</html>");
            return end >= 0 ? doc.substring(0, end + "</html>".length()) : stripTrailingFence(doc);
        }
        Matcher m = FRAGMENT.matcher(t);
        if (m.find()) {
            return PREFIX + stripTrailingFence(t.substring(m.start())) + "</body></html>";
        }
        return null;
    }

    /** Removes a closing ``` fence and any short chatter after it. */
    private static String stripTrailingFence(String s) {
        int i = s.lastIndexOf("```");
        if (i >= 0 && s.length() - i < 400) {
            return s.substring(0, i).trim();
        }
        return s.trim();
    }
}
