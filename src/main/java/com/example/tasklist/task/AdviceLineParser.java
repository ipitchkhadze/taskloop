package com.example.tasklist.task;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts checklist lines from LM Studio advice text.
 * <p>First tries numbered/bullet lists; if nothing matches (nested markdown, prose, odd formatting),
 * falls back to paragraphs, then lines, then the whole text as a single task — so spawn-board always
 * has material to import whenever advice is non-empty.
 */
@Component
public class AdviceLineParser {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_ITEMS = 50;
    /** Lines shorter than this are skipped in fallback (noise like "---", "**"). */
    private static final int MIN_FALLBACK_LINE = 1;

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s.*$");
    /**
     * Dot or closing paren after the index (ASCII + fullwidth / CJK punctuation models sometimes emit).
     */
    private static final String NUM_SUFFIX = "[.\\uFF0E\\u3002)\\uFF09]";
    /** Allows optional markdown emphasis around list markers, e.g. "**1.** Step", "1. Step", "1)Step". */
    private static final Pattern NUMBERED = Pattern.compile(
            "^\\s*(?:\\*{0,3}\\s*)?\\p{Nd}+\\s*" + NUM_SUFFIX + "\\s*(?:\\*{0,3}\\s*)?(.+)$");
    /** "1 – foo", "1—foo", "1 - foo" (models often use dash instead of a dot). */
    private static final Pattern NUMBERED_DASH = Pattern.compile(
            "^\\s*(?:\\*{0,3}\\s*)?\\p{Nd}+\\s*[–\\-—]\\s*(.+)$");
    /** "(1) foo" */
    private static final Pattern NUMBERED_PAREN = Pattern.compile(
            "^\\s*(?:\\*{0,3}\\s*)?\\(\\p{Nd}+\\)\\s*(?:\\*{0,3}\\s*)?(.+)$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*\\u2022]\\s+(.+)$");
    private static final Pattern DECORATIVE_ONLY = Pattern.compile("^[\\s\\-*_=#`•]+$");

    /**
     * Structured list items, or if none found — paragraphs / lines / full text (never empty for non-blank advice).
     */
    public List<String> parseItemTitles(String advice) {
        if (advice == null || advice.isBlank()) {
            return List.of();
        }
        List<String> structured = parseStructuredItems(advice);
        if (!structured.isEmpty()) {
            return dedupeTitles(structured);
        }
        return dedupeTitles(fallbackTitles(advice));
    }

    /**
     * Drops repeated lines the model often emits twice (same header + body, with/without emoji).
     */
    private static List<String> dedupeTitles(List<String> titles) {
        if (titles.size() <= 1) {
            return titles;
        }
        List<String> out = new ArrayList<>(titles.size());
        Set<String> seen = new HashSet<>();
        for (String t : titles) {
            String key = normalizeTitleKey(t);
            if (key.isEmpty()) {
                continue;
            }
            if (seen.add(key)) {
                out.add(t);
            }
        }
        return out.isEmpty() ? titles : out;
    }

    /** Case- and markdown-insensitive key; strips leading emoji / punctuation so duplicates still match. */
    private static String normalizeTitleKey(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim().replace('\u00a0', ' ');
        t = t.replace("*", "");
        t = t.replaceAll("\\s+", " ").trim();
        t = t.toLowerCase(Locale.ROOT);
        t = t.replaceFirst("^[^\\p{L}\\p{N}]+", "");
        return t.trim();
    }

    private List<String> parseStructuredItems(String advice) {
        List<String> out = new ArrayList<>();
        for (String rawLine : normalizeListNewlines(advice).split("\\R")) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            String line = rawLine.trim().replace('\u00a0', ' ');
            if (line.isEmpty()) {
                continue;
            }
            if (MARKDOWN_HEADING.matcher(line).matches()) {
                continue;
            }
            String body = matchBody(line);
            if (body == null || body.isBlank()) {
                continue;
            }
            body = truncate(body.trim());
            if (!body.isEmpty()) {
                out.add(body);
            }
        }
        return out;
    }

    private List<String> fallbackTitles(String advice) {
        String normalized = advice.replace('\u00a0', ' ').trim();
        List<String> out = new ArrayList<>();

        String[] paras = normalized.split("\\R{2,}");
        if (paras.length > 1) {
            for (String p : paras) {
                if (out.size() >= MAX_ITEMS) {
                    break;
                }
                String t = cleanFallbackChunk(p);
                if (isFallbackChunkUseful(t)) {
                    out.add(truncate(t));
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }

        for (String raw : normalized.split("\\R")) {
            if (out.size() >= MAX_ITEMS) {
                break;
            }
            String t = cleanFallbackChunk(raw);
            if (isFallbackChunkUseful(t)) {
                out.add(truncate(t));
            }
        }
        if (!out.isEmpty()) {
            return out;
        }

        String whole = cleanFallbackChunk(normalized);
        if (!whole.isEmpty()) {
            out.add(truncate(whole));
            return out;
        }
        out.add(truncate(normalized.replaceAll("\\s+", " ").trim()));
        return out;
    }

    private static boolean isFallbackChunkUseful(String t) {
        if (t == null || t.length() < MIN_FALLBACK_LINE) {
            return false;
        }
        return !DECORATIVE_ONLY.matcher(t).matches();
    }

    /**
     * Strip markdown-ish noise so nested lists and prose lines still yield readable task titles.
     */
    private static String cleanFallbackChunk(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String t = raw.trim().replace('\u00a0', ' ');
        t = t.replaceFirst("^#{1,6}\\s+", "");
        t = t.replaceFirst("^>\\s*", "");
        // indented bullets / numbered lines (repeat shallow strip)
        for (int i = 0; i < 4; i++) {
            String next = t
                    .replaceFirst("^\\s{1,12}[-*•\\u2022▪‣]\\s*", "")
                    .replaceFirst("^\\s{0,12}\\p{Nd}{1,3}\\s*[.\\uFF0E\\u3002)\\uFF09]\\s*", "")
                    .replaceFirst("^\\s{0,12}\\(\\p{Nd}+\\)\\s*", "");
            if (next.equals(t)) {
                break;
            }
            t = next.trim();
        }
        t = stripEdgeMarkdownStars(t);
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    /**
     * LM output often puts the whole numbered list in one line ("1. … 2. …"). Break before each new index.
     * Lookahead requires whitespace after the list marker so decimals like "3.14" are not split as "3." items.
     */
    private static String normalizeListNewlines(String advice) {
        String t = advice.replace('\u00a0', ' ');
        t = t.replaceAll(
                "\\s+(?=\\p{Nd}{1,3}\\s*" + NUM_SUFFIX + "\\s+(?:\\*{0,3}\\s*)?\\S)",
                "\n");
        t = t.replaceAll(
                "\\s+(?=\\p{Nd}{1,3}\\s*[–\\-—]\\s*\\S)",
                "\n");
        return t;
    }

    private static String matchBody(String line) {
        Matcher m = NUMBERED.matcher(line);
        if (m.matches()) {
            return stripEdgeMarkdownStars(m.group(1));
        }
        m = NUMBERED_DASH.matcher(line);
        if (m.matches()) {
            return stripEdgeMarkdownStars(m.group(1));
        }
        m = NUMBERED_PAREN.matcher(line);
        if (m.matches()) {
            return stripEdgeMarkdownStars(m.group(1));
        }
        m = BULLET.matcher(line);
        if (m.matches()) {
            return stripEdgeMarkdownStars(m.group(1));
        }
        return null;
    }

    /** Trim accidental markdown emphasis at the start/end of an item title. */
    private static String stripEdgeMarkdownStars(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().replaceAll("^\\*+", "").replaceAll("\\s*\\*+$", "").trim();
        return t;
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_TITLE_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_TITLE_LENGTH);
    }
}
