package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeSpaceFilterTest {

    @Test
    void filtersPrivateSpaceByOwnerUserId() {
        List<SearchResult> filtered = KnowledgeSpaceFilter.filter(List.of(
                result("mine", "PRIVATE", "1"),
                result("other", "PRIVATE", "2")
        ), "1", "PRIVATE", null);

        assertEquals(List.of("mine"), filtered.stream().map(SearchResult::getFileMd5).toList());
    }

    private SearchResult result(String md5, String scope, String userId) {
        return new SearchResult(md5, 1, null, "text", null, 1.0, userId, null, false, md5 + ".md", scope, null);
    }
}
