package com.yuki.enterprise_private_rag_qa.utils;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;

/**
 * 从检索结果中提取与问题最相关的上下文片段，避免整段父块淹没关键事实。
 */
public final class ContextSnippetUtils {

    private static final int DEFAULT_EXCERPT_RADIUS = 180;
    private static final int DEFAULT_MAX_LENGTH = 700;

    private ContextSnippetUtils() {
    }

    public static String extractExcerpt(String query, SearchResult result) {
        return extractExcerpt(query, result, DEFAULT_MAX_LENGTH);
    }

    public static String extractExcerpt(String query, SearchResult result, int maxLength) {
        if (result == null) {
            return "";
        }
        String child = normalize(result.getTextContent());
        String parent = normalize(result.getParentTextContent());
        String corpus = !parent.isBlank() ? parent : child;

        int matchIndex = findBestMatchIndex(query, child);
        String matchSource = child;
        if (matchIndex < 0 && !parent.isBlank()) {
            matchIndex = findBestMatchIndex(query, parent);
            matchSource = parent;
        }
        if (matchIndex >= 0) {
            int matchLen = longestMatchedPrefix(query, matchSource, matchIndex);
            return trimToLength(excerptAround(matchSource, matchIndex, matchLen, DEFAULT_EXCERPT_RADIUS), maxLength);
        }
        if (!child.isBlank() && child.length() <= maxLength) {
            return child;
        }
        if (!corpus.isBlank()) {
            return trimToLength(corpus, maxLength);
        }
        return "";
    }

    private static int findBestMatchIndex(String query, String text) {
        if (query == null || query.isBlank() || text == null || text.isBlank()) {
            return -1;
        }
        String normalizedQuery = query.replaceAll("\\s+", "").trim();
        if (normalizedQuery.isEmpty()) {
            return -1;
        }
        if (text.contains(normalizedQuery)) {
            return text.indexOf(normalizedQuery);
        }
        for (int len = normalizedQuery.length(); len >= 2; len--) {
            String prefix = normalizedQuery.substring(0, len);
            int idx = text.indexOf(prefix);
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    private static int longestMatchedPrefix(String query, String text, int index) {
        String normalizedQuery = query.replaceAll("\\s+", "").trim();
        int maxLen = Math.min(normalizedQuery.length(), text.length() - index);
        for (int len = maxLen; len >= 2; len--) {
            if (text.regionMatches(index, normalizedQuery, 0, len)) {
                return len;
            }
        }
        return Math.min(2, maxLen);
    }

    private static String excerptAround(String text, int index, int matchLen, int radius) {
        int start = Math.max(0, index - radius);
        int end = Math.min(text.length(), index + matchLen + radius);
        StringBuilder builder = new StringBuilder();
        if (start > 0) {
            builder.append("…");
        }
        builder.append(text, start, end);
        if (end < text.length()) {
            builder.append("…");
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private static String trimToLength(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
