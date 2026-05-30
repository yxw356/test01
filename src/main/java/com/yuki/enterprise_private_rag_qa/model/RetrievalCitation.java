package com.yuki.enterprise_private_rag_qa.model;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private static final int SNIPPET_MAX = 300;

    public static RetrievalCitation fromSearchResult(int index, SearchResult result) {
        RetrievalCitation citation = new RetrievalCitation();
        citation.setIndex(index);
        citation.setFileMd5(result.getFileMd5());
        citation.setFileName(result.getFileName());
        citation.setChunkId(result.getChunkId());
        citation.setParentId(result.getParentId());
        citation.setScore(result.getScore());
        citation.setSnippet(truncateSnippet(result));
        return citation;
    }

    private static String truncateSnippet(SearchResult result) {
        String text = result.getParentTextContent();
        if (text == null || text.isBlank()) {
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
