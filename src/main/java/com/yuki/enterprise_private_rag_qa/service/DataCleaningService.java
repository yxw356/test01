package com.yuki.enterprise_private_rag_qa.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class DataCleaningService {

    @Value("${knowledge.cleaning.normalize-line-breaks:true}")
    private boolean normalizeLineBreaks = true;

    @Value("${knowledge.cleaning.normalize-unicode-spaces:true}")
    private boolean normalizeUnicodeSpaces = true;

    @Value("${knowledge.cleaning.normalize-whitespace:true}")
    private boolean normalizeWhitespace = true;

    @Value("${knowledge.cleaning.trim-lines:true}")
    private boolean trimLines = true;

    @Value("${knowledge.cleaning.collapse-blank-lines:true}")
    private boolean collapseBlankLines = true;

    @Value("${knowledge.cleaning.remove-duplicate-lines:true}")
    private boolean removeDuplicateLines = true;

    @Value("${knowledge.cleaning.min-duplicate-line-length:8}")
    private int minDuplicateLineLength = 8;

    @Value("${knowledge.cleaning.drop-line-patterns:}")
    private String dropLinePatterns = "";

    public CleaningResult clean(String rawText) {
        return clean(rawText, currentDefaultConfig());
    }

    public CleaningResult clean(String rawText, CleaningRuleConfig config) {
        CleaningRuleConfig effectiveConfig = config == null ? currentDefaultConfig() : config.normalized();
        if (rawText == null || rawText.isBlank()) {
            return new CleaningResult("", rawText == null ? 0 : rawText.length(), 0, rawText == null ? 0 : rawText.length(), 0);
        }

        String normalized = normalizeDocumentText(rawText, effectiveConfig);
        List<Pattern> dropPatterns = compileDropLinePatterns(effectiveConfig.dropLinePatterns());

        List<String> cleanedLines = new ArrayList<>();
        Set<String> seenMeaningfulLines = new HashSet<>();
        int duplicateLinesRemoved = 0;
        boolean previousBlank = false;

        for (String rawLine : normalized.split("\n", -1)) {
            String line = normalizeLine(rawLine, effectiveConfig);
            if (shouldDropLine(line, dropPatterns)) {
                continue;
            }
            if (line.isBlank()) {
                if (!effectiveConfig.collapseBlankLines()) {
                    cleanedLines.add(line);
                } else if (!previousBlank && !cleanedLines.isEmpty()) {
                    cleanedLines.add("");
                    previousBlank = true;
                }
                continue;
            }

            previousBlank = false;
            if (effectiveConfig.removeDuplicateLines() && isMeaningfulDuplicate(line, seenMeaningfulLines, effectiveConfig.minDuplicateLineLength())) {
                duplicateLinesRemoved++;
                continue;
            }
            cleanedLines.add(line);
        }

        while (!cleanedLines.isEmpty() && cleanedLines.get(cleanedLines.size() - 1).isBlank()) {
            cleanedLines.remove(cleanedLines.size() - 1);
        }

        String cleanedText = String.join("\n", cleanedLines).trim();
        int originalChars = rawText.length();
        int cleanedChars = cleanedText.length();
        return new CleaningResult(
                cleanedText,
                originalChars,
                cleanedChars,
                Math.max(0, originalChars - cleanedChars),
                duplicateLinesRemoved
        );
    }

    public CleaningRuleConfig currentDefaultConfig() {
        return new CleaningRuleConfig(
                normalizeLineBreaks,
                normalizeUnicodeSpaces,
                normalizeWhitespace,
                trimLines,
                collapseBlankLines,
                removeDuplicateLines,
                minDuplicateLineLength,
                parseDropLinePatterns(dropLinePatterns)
        ).normalized();
    }

    private String normalizeDocumentText(String rawText, CleaningRuleConfig config) {
        String normalized = rawText;
        if (config.normalizeLineBreaks()) {
            normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');
        }
        if (config.normalizeUnicodeSpaces()) {
            normalized = normalized.replace('\u00A0', ' ').replace('\u3000', ' ');
        }
        return normalized;
    }

    private String normalizeLine(String rawLine, CleaningRuleConfig config) {
        String line = rawLine;
        if (config.normalizeWhitespace()) {
            line = line.replaceAll("[\\t ]+", " ");
        }
        return config.trimLines() ? line.trim() : line;
    }

    private boolean isMeaningfulDuplicate(String line, Set<String> seenMeaningfulLines, int minLength) {
        if (line.length() < minLength) {
            return false;
        }
        if (seenMeaningfulLines.contains(line)) {
            return true;
        }
        seenMeaningfulLines.add(line);
        return false;
    }

    private boolean shouldDropLine(String line, List<Pattern> dropPatterns) {
        if (dropPatterns.isEmpty()) {
            return false;
        }
        return dropPatterns.stream().anyMatch(pattern -> pattern.matcher(line).matches());
    }

    private List<Pattern> compileDropLinePatterns(List<String> patternTexts) {
        if (patternTexts == null || patternTexts.isEmpty()) {
            return List.of();
        }
        List<Pattern> patterns = new ArrayList<>();
        for (String patternText : patternTexts) {
            if (patternText == null || patternText.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(patternText));
            } catch (PatternSyntaxException ignored) {
                // 跳过非法规则，避免单条配置导致整份文档清洗失败。
            }
        }
        return patterns;
    }

    private List<String> parseDropLinePatterns(String rawPatterns) {
        if (rawPatterns == null || rawPatterns.isBlank()) {
            return List.of();
        }
        return List.of(rawPatterns.split("\\s*;;\\s*")).stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .toList();
    }

    public record CleaningRuleConfig(
            boolean normalizeLineBreaks,
            boolean normalizeUnicodeSpaces,
            boolean normalizeWhitespace,
            boolean trimLines,
            boolean collapseBlankLines,
            boolean removeDuplicateLines,
            int minDuplicateLineLength,
            List<String> dropLinePatterns
    ) {
        public static CleaningRuleConfig defaultConfig() {
            return new CleaningRuleConfig(true, true, true, true, true, true, 8, List.of());
        }

        CleaningRuleConfig normalized() {
            return new CleaningRuleConfig(
                    normalizeLineBreaks,
                    normalizeUnicodeSpaces,
                    normalizeWhitespace,
                    trimLines,
                    collapseBlankLines,
                    removeDuplicateLines,
                    Math.max(1, minDuplicateLineLength),
                    dropLinePatterns == null ? List.of() : List.copyOf(dropLinePatterns)
            );
        }

        public CleaningRuleConfig withRemoveDuplicateLines(boolean value) {
            return new CleaningRuleConfig(
                    normalizeLineBreaks, normalizeUnicodeSpaces, normalizeWhitespace, trimLines,
                    collapseBlankLines, value, minDuplicateLineLength, dropLinePatterns
            );
        }

        public CleaningRuleConfig withDropLinePatterns(List<String> value) {
            return new CleaningRuleConfig(
                    normalizeLineBreaks, normalizeUnicodeSpaces, normalizeWhitespace, trimLines,
                    collapseBlankLines, removeDuplicateLines, minDuplicateLineLength, value
            );
        }
    }

    public record CleaningResult(
            String cleanedText,
            int originalChars,
            int cleanedChars,
            int removedChars,
            int duplicateLinesRemoved
    ) {
        public double compressionRatio() {
            if (originalChars == 0) {
                return 0.0d;
            }
            return (double) removedChars / originalChars;
        }
    }
}
