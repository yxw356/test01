package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;

import java.util.List;
import java.util.Objects;

/**
 * Filters retrieval results by active knowledge space context.
 */
public final class KnowledgeSpaceFilter {

    private KnowledgeSpaceFilter() {
    }

    public static List<SearchResult> filter(List<SearchResult> results, String userId,
                                            String knowledgeScope, String departmentId) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        if (knowledgeScope == null || knowledgeScope.isBlank()) {
            return results;
        }

        String scope = knowledgeScope.trim();
        if ("PUBLIC".equalsIgnoreCase(scope)) {
            return results.stream()
                    .filter(result -> "PUBLIC".equalsIgnoreCase(result.getKnowledgeScope())
                            || Boolean.TRUE.equals(result.getIsPublic()))
                    .toList();
        }
        if ("DEPARTMENT".equalsIgnoreCase(scope) && departmentId != null && !departmentId.isBlank()) {
            return results.stream()
                    .filter(result -> "DEPARTMENT".equalsIgnoreCase(result.getKnowledgeScope()))
                    .filter(result -> departmentId.equals(result.getDepartmentId())
                            || departmentId.equals(result.getOrgTag()))
                    .toList();
        }
        if ("PRIVATE".equalsIgnoreCase(scope) && userId != null && !userId.isBlank()) {
            return results.stream()
                    .filter(result -> "PRIVATE".equalsIgnoreCase(result.getKnowledgeScope()))
                    .filter(result -> Objects.equals(userId, result.getUserId()))
                    .toList();
        }
        return results;
    }
}
