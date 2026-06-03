package com.yuki.enterprise_private_rag_qa.utils;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextSnippetUtilsTest {

    @Test
    void shouldExtractExcerptAroundQueryMatch() {
        SearchResult result = new SearchResult(
                "md5",
                2,
                "parent",
                "项目总投资4亿元人民币基地占地面积200亩国际一流设备",
                "项目总投资4亿元人民币基地占地面积200亩国际一流设备",
                2.83,
                "1",
                "default",
                true,
                "demo.pdf"
        );

        String excerpt = ContextSnippetUtils.extractExcerpt("基地占地面积", result, 200);

        assertTrue(excerpt.contains("基地占地面积200亩"), excerpt);
    }
}
