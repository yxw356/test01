package com.yuki.enterprise_private_rag_qa.model;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import com.yuki.enterprise_private_rag_qa.utils.ContextSnippetUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 问答检索引用（结构化落库与前端展示）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalCitation {
    private int index;
    private String fileMd5;
    private String fileName;
    private Integer chunkId;
    private String parentId;
    private Double score;
    private String snippet;
    private String previewUrl;
    private String downloadUrl;

    private static final int SNIPPET_MAX = 300;
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(
            "(?:文件名称|文件名|制度名称)[:：]\\s*(.{2,80}?)(?=文件编号|生效日期|状态|文件版本|页数|版号|编制人|审核人|批准人|\\s|。|；|;|，|,|《|》|$)"
    );
    private static final Pattern BOOK_TITLE_PATTERN = Pattern.compile("《([^》]{2,80})》");

    public static RetrievalCitation fromSearchResult(int index, SearchResult result) {
        return fromSearchResult(index, result, null);
    }

    public static RetrievalCitation fromSearchResult(int index, SearchResult result, String query) {
        RetrievalCitation citation = new RetrievalCitation();
        citation.setIndex(index);
        citation.setFileMd5(result.getFileMd5());
        citation.setChunkId(result.getChunkId());
        citation.setParentId(result.getParentId());
        citation.setScore(result.getScore());
        citation.setSnippet(truncateSnippet(result, query));
        String resolvedFileName = resolveFileName(result.getFileName(), citation.getSnippet());
        citation.setFileName(resolvedFileName);
        citation.setPreviewUrl(buildDocumentUrl("/api/v1/documents/preview", result, resolvedFileName));
        citation.setDownloadUrl(buildDocumentUrl("/api/v1/documents/download", result, resolvedFileName));
        return citation;
    }

    private static String buildDocumentUrl(String path, SearchResult result, String resolvedFileName) {
        String fileMd5 = result.getFileMd5();
        if (fileMd5 != null && !fileMd5.isBlank()) {
            return path + "?fileMd5=" + URLEncoder.encode(fileMd5, StandardCharsets.UTF_8);
        }
        String fileName = resolvedFileName;
        if (fileName != null && !fileName.isBlank()) {
            return path + "?fileName=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        }
        return path;
    }

    private static String resolveFileName(String explicitName, String snippet) {
        if (explicitName != null && !explicitName.isBlank()) {
            return explicitName;
        }
        String inferred = inferFileName(snippet);
        return inferred.isBlank() ? explicitName : inferred;
    }

    private static String inferFileName(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher fileNameMatcher = FILE_NAME_PATTERN.matcher(text);
        if (fileNameMatcher.find()) {
            return sanitizeName(fileNameMatcher.group(1));
        }
        Matcher bookTitleMatcher = BOOK_TITLE_PATTERN.matcher(text);
        if (bookTitleMatcher.find()) {
            return sanitizeName(bookTitleMatcher.group(1));
        }
        return "";
    }

    private static String sanitizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s　]+", "")
                .replaceAll("^(公司|江西龙汇肉制品有限责任公司)", "")
                .trim();
    }

    private static String truncateSnippet(SearchResult result, String query) {
        String text = ContextSnippetUtils.extractExcerpt(query, result, SNIPPET_MAX);
        if (text.isBlank()) {
            text = result.getTextContent();
        }
        if (text == null) {
            return "";
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() <= SNIPPET_MAX) {
            return text;
        }
        return text.substring(0, SNIPPET_MAX) + "…";
    }
}
