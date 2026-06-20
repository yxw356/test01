package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.client.DeepSeekClient;
import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import com.yuki.enterprise_private_rag_qa.service.rag.RagPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ChatHandlerKnowledgeSpaceTest {

    @Test
    void filtersRetrievedDocumentsBySelectedDepartmentSpace() {
        ChatHandler handler = handler();
        List<SearchResult> filtered = handler.filterByKnowledgeSpace(List.of(
                result("public", "PUBLIC", null),
                result("hr", "DEPARTMENT", "HR"),
                result("fin", "DEPARTMENT", "FIN")
        ), new ChatHandler.KnowledgeSpaceContext("DEPARTMENT", "HR"));

        assertEquals(List.of("hr"), filtered.stream().map(SearchResult::getFileMd5).toList());
    }

    @Test
    void filtersRetrievedDocumentsByPublicSpace() {
        ChatHandler handler = handler();
        List<SearchResult> filtered = handler.filterByKnowledgeSpace(List.of(
                result("public", "PUBLIC", null),
                result("hr", "DEPARTMENT", "HR")
        ), new ChatHandler.KnowledgeSpaceContext("PUBLIC", null));

        assertEquals(List.of("public"), filtered.stream().map(SearchResult::getFileMd5).toList());
    }

    @Test
    void filtersRetrievedDocumentsByPrivateSpace() {
        ChatHandler handler = handler();
        List<SearchResult> filtered = handler.filterByKnowledgeSpace(List.of(
                result("mine", "PRIVATE", "1", "1"),
                result("other", "PRIVATE", "2", "2")
        ), new ChatHandler.KnowledgeSpaceContext("PRIVATE", null), "1");

        assertEquals(List.of("mine"), filtered.stream().map(SearchResult::getFileMd5).toList());
    }

    private ChatHandler handler() {
        return new ChatHandler(
                mock(RedisTemplate.class),
                mock(HybridSearchService.class),
                mock(DeepSeekClient.class),
                mock(RagPipeline.class),
                mock(AuditService.class),
                mock(OperationMetricsService.class),
                mock(ConversationService.class),
                mock(ChatConcurrencyLimiter.class),
                false,
                true
        );
    }

    private SearchResult result(String md5, String scope, String departmentId) {
        return result(md5, scope, departmentId, "1");
    }

    private SearchResult result(String md5, String scope, String departmentId, String userId) {
        return new SearchResult(md5, 1, null, "text", null, 1.0, userId, departmentId, "PUBLIC".equals(scope), md5 + ".md",
                scope, departmentId);
    }
}
