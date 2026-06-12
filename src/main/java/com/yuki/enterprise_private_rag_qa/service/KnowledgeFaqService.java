package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeFaq;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeFaqRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeFaqService {

    private final KnowledgeFaqRepository repository;
    private final DocumentPermissionService permissionService;

    public KnowledgeFaqService(KnowledgeFaqRepository repository,
                               DocumentPermissionService permissionService) {
        this.repository = repository;
        this.permissionService = permissionService;
    }

    public record FaqRequest(
            String question,
            String answer,
            String aliases,
            FileUpload.KnowledgeScope knowledgeScope,
            String departmentId,
            Boolean enabled
    ) {
    }

    public record FaqMatch(Long id, String question, String answer) {
    }

    public KnowledgeFaq create(String userId, FaqRequest request) {
        User user = permissionService.requireUser(userId);
        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? FileUpload.KnowledgeScope.PUBLIC
                : request.knowledgeScope();
        String departmentId = normalizeDepartment(scope, request.departmentId(), user);
        ensureManagePermission(user, scope, departmentId);

        if (isBlank(request.question()) || isBlank(request.answer())) {
            throw new CustomException("标准问题和答案不能为空", HttpStatus.BAD_REQUEST);
        }

        KnowledgeFaq faq = new KnowledgeFaq();
        faq.setQuestion(request.question().trim());
        faq.setAnswer(request.answer().trim());
        faq.setAliases(request.aliases());
        faq.setKnowledgeScope(scope);
        faq.setDepartmentId(departmentId);
        faq.setEnabled(request.enabled() == null || request.enabled());
        faq.setCreatedBy(userId);
        return repository.save(faq);
    }

    public KnowledgeFaq update(String userId, Long id, FaqRequest request) {
        User user = permissionService.requireUser(userId);
        KnowledgeFaq faq = repository.findById(id)
                .orElseThrow(() -> new CustomException("问答对不存在", HttpStatus.NOT_FOUND));
        ensureManagePermission(user, faq.getKnowledgeScope(), faq.getDepartmentId());

        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? faq.getKnowledgeScope()
                : request.knowledgeScope();
        String departmentId = normalizeDepartment(scope, request.departmentId(), user);
        ensureManagePermission(user, scope, departmentId);

        if (isBlank(request.question()) || isBlank(request.answer())) {
            throw new CustomException("标准问题和答案不能为空", HttpStatus.BAD_REQUEST);
        }

        faq.setQuestion(request.question().trim());
        faq.setAnswer(request.answer().trim());
        faq.setAliases(request.aliases());
        faq.setKnowledgeScope(scope);
        faq.setDepartmentId(departmentId);
        if (request.enabled() != null) {
            faq.setEnabled(request.enabled());
        }
        return repository.save(faq);
    }

    public void delete(String userId, Long id) {
        User user = permissionService.requireUser(userId);
        KnowledgeFaq faq = repository.findById(id)
                .orElseThrow(() -> new CustomException("问答对不存在", HttpStatus.NOT_FOUND));
        ensureManagePermission(user, faq.getKnowledgeScope(), faq.getDepartmentId());
        repository.delete(faq);
    }

    public List<KnowledgeFaq> listVisible(String userId) {
        User user = permissionService.requireUser(userId);
        return repository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .filter(item -> canView(user, item.getKnowledgeScope(), item.getDepartmentId()))
                .toList();
    }

    public List<KnowledgeFaq> listManageable(String userId) {
        User user = permissionService.requireUser(userId);
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(item -> canView(user, item.getKnowledgeScope(), item.getDepartmentId()))
                .toList();
    }

    public Optional<FaqMatch> findExactMatch(String query, String userId) {
        if (isBlank(query)) {
            return Optional.empty();
        }
        User user = permissionService.requireUser(userId);
        String normalizedQuery = normalizeText(query);
        return repository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .filter(item -> canView(user, item.getKnowledgeScope(), item.getDepartmentId()))
                .filter(item -> labelsOf(item.getQuestion(), item.getAliases()).stream()
                        .map(KnowledgeFaqService::normalizeText)
                        .anyMatch(normalizedQuery::equals))
                .findFirst()
                .map(item -> new FaqMatch(item.getId(), item.getQuestion(), item.getAnswer()));
    }

    private void ensureManagePermission(User user, FileUpload.KnowledgeScope scope, String departmentId) {
        if (scope == FileUpload.KnowledgeScope.PUBLIC && !permissionService.canUploadPublic(user)) {
            throw new CustomException("没有管理公共问答对的权限", HttpStatus.FORBIDDEN);
        }
        if (scope == FileUpload.KnowledgeScope.DEPARTMENT && !permissionService.canUploadDepartment(user, departmentId)) {
            throw new CustomException("没有管理该部门问答对的权限", HttpStatus.FORBIDDEN);
        }
        if (scope == FileUpload.KnowledgeScope.PRIVATE) {
            throw new CustomException("暂不支持私人问答对", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean canView(User user, FileUpload.KnowledgeScope scope, String departmentId) {
        if (scope == FileUpload.KnowledgeScope.PUBLIC) {
            return true;
        }
        FileUpload file = new FileUpload();
        file.setKnowledgeScope(scope);
        file.setDepartmentId(departmentId);
        file.setOrgTag(departmentId);
        file.setUserId(String.valueOf(user.getId()));
        return permissionService.canView(user, file);
    }

    private String normalizeDepartment(FileUpload.KnowledgeScope scope, String departmentId, User user) {
        if (scope == FileUpload.KnowledgeScope.PUBLIC) {
            return null;
        }
        if (!isBlank(departmentId)) {
            return departmentId.trim();
        }
        if (!isBlank(user.getPrimaryOrg())) {
            return user.getPrimaryOrg();
        }
        if (!isBlank(user.getOrgTags())) {
            return user.getOrgTags().split(",")[0].trim();
        }
        return null;
    }

    private static List<String> labelsOf(String main, String aliases) {
        return Arrays.stream((main + "\n" + (aliases == null ? "" : aliases)).split("[,，;；\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）()【】\\[\\]《》<>]+", "")
                .trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
