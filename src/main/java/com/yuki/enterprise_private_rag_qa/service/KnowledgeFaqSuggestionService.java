package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeFaqSuggestion;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeFaqSuggestionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeFaqSuggestionService {

    private static final int MIN_QUESTION_LENGTH = 4;
    private static final int ANSWER_MAX_LENGTH = 1200;

    private final KnowledgeFaqSuggestionRepository repository;
    private final DocumentPermissionService permissionService;

    public KnowledgeFaqSuggestionService(KnowledgeFaqSuggestionRepository repository,
                                         DocumentPermissionService permissionService) {
        this.repository = repository;
        this.permissionService = permissionService;
    }

    public void recordCandidate(String userId, String question, String answer, List<SearchResult> evidence) {
        if (isBlank(question) || question.trim().length() < MIN_QUESTION_LENGTH || isWeakAnswer(answer) || evidence == null || evidence.isEmpty()) {
            return;
        }
        String normalized = KnowledgeFaqService.normalizeText(question);
        if (normalized.isBlank()) {
            return;
        }

        SearchResult first = evidence.get(0);
        FileUpload.KnowledgeScope scope = "PUBLIC".equalsIgnoreCase(first.getKnowledgeScope())
                || Boolean.TRUE.equals(first.getIsPublic())
                ? FileUpload.KnowledgeScope.PUBLIC
                : FileUpload.KnowledgeScope.DEPARTMENT;
        String departmentId = first.getDepartmentId() != null ? first.getDepartmentId() : first.getOrgTag();

        KnowledgeFaqSuggestion suggestion = repository.findByNormalizedQuestion(normalized)
                .orElseGet(KnowledgeFaqSuggestion::new);
        if (suggestion.getId() == null) {
            suggestion.setNormalizedQuestion(normalized);
            suggestion.setQuestion(question.trim());
            suggestion.setCreatedBy(userId);
        } else {
            suggestion.setHitCount(suggestion.getHitCount() + 1);
        }
        suggestion.setSuggestedAnswer(trimAnswer(answer));
        suggestion.setKnowledgeScope(scope);
        suggestion.setDepartmentId(scope == FileUpload.KnowledgeScope.PUBLIC ? null : departmentId);
        suggestion.setEvidenceCount(evidence.size());
        suggestion.setLastAskedAt(LocalDateTime.now());
        if (suggestion.getStatus() == KnowledgeFaqSuggestion.SuggestionStatus.IGNORED && suggestion.getHitCount() >= 3) {
            suggestion.setStatus(KnowledgeFaqSuggestion.SuggestionStatus.PENDING);
        }
        repository.save(suggestion);
    }

    public List<KnowledgeFaqSuggestion> listVisible(String userId) {
        User user = permissionService.requireUser(userId);
        return repository.findAllByOrderByHitCountDescUpdatedAtDesc().stream()
                .filter(item -> canViewSuggestion(user, item))
                .toList();
    }

    public KnowledgeFaqSuggestion updateStatus(String userId, Long id, KnowledgeFaqSuggestion.SuggestionStatus status) {
        User user = permissionService.requireUser(userId);
        KnowledgeFaqSuggestion suggestion = repository.findById(id)
                .orElseThrow(() -> new CustomException("问答建议不存在", HttpStatus.NOT_FOUND));
        if (!canManageSuggestion(user, suggestion)) {
            throw new CustomException("没有管理该问答建议的权限", HttpStatus.FORBIDDEN);
        }
        suggestion.setStatus(status == null ? KnowledgeFaqSuggestion.SuggestionStatus.PENDING : status);
        return repository.save(suggestion);
    }

    private boolean canViewSuggestion(User user, KnowledgeFaqSuggestion suggestion) {
        if (suggestion.getKnowledgeScope() == FileUpload.KnowledgeScope.PUBLIC) {
            return user.isSuperAdmin() || user.getRole() == User.Role.KNOWLEDGE_ADMIN;
        }
        return canManageSuggestion(user, suggestion);
    }

    private boolean canManageSuggestion(User user, KnowledgeFaqSuggestion suggestion) {
        if (suggestion.getKnowledgeScope() == FileUpload.KnowledgeScope.PUBLIC) {
            return permissionService.canUploadPublic(user);
        }
        return permissionService.canUploadDepartment(user, suggestion.getDepartmentId());
    }

    private boolean isWeakAnswer(String answer) {
        if (isBlank(answer)) {
            return true;
        }
        String normalized = answer.toLowerCase(Locale.ROOT);
        return normalized.contains("没有找到相关依据")
                || normalized.contains("知识库没有找到")
                || normalized.contains("暂无相关信息")
                || normalized.contains("无法确认");
    }

    private static String trimAnswer(String answer) {
        String value = answer == null ? "" : answer.trim();
        return value.length() <= ANSWER_MAX_LENGTH ? value : value.substring(0, ANSWER_MAX_LENGTH) + "…";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
