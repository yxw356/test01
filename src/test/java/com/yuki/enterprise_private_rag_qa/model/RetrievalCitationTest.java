package com.yuki.enterprise_private_rag_qa.model;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalCitationTest {

    @Test
    void infersFileNameFromSnippetWhenSearchResultHasNoFileName() {
        SearchResult result = new SearchResult(
                "travel-md5",
                1,
                "江西龙汇肉制品有限责任公司文件名称：出差管理制度文件编号：LHRZP-03-RS-003生效日期：2023-3-1",
                0.91
        );

        RetrievalCitation citation = RetrievalCitation.fromSearchResult(1, result, "出差住宿标准");

        assertEquals("出差管理制度", citation.getFileName());
        assertTrue(citation.getPreviewUrl().contains("fileMd5=travel-md5"));
    }

    @Test
    void keepsExplicitFileNameFirst() {
        SearchResult result = new SearchResult(
                "travel-md5",
                1,
                "文件名称：出差管理制度",
                0.91,
                "正式上传文件.pdf"
        );

        RetrievalCitation citation = RetrievalCitation.fromSearchResult(1, result, "出差住宿标准");

        assertEquals("正式上传文件.pdf", citation.getFileName());
    }
}
