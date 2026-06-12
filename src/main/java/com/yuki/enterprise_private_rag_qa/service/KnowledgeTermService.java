package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeTerm;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeTermRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class KnowledgeTermService {

    private static final List<BuiltinTerm> BUILTIN_TERMS = List.of(
            new BuiltinTerm(
                    "一级部门负责人",
                    "公司部门管理层级中的一级部门负责人，常见岗位表达包括总监、部门总监、销售总监、研发部经理等。",
                    "一级部门负责人,部门一级负责人,部门负责人,一级负责人,总监,部门总监,销售总监,研发部经理"
            ),
            new BuiltinTerm(
                    "差旅标准",
                    "员工出差涉及的交通、住宿、餐饮、补贴和报销边界。",
                    "差旅标准,出差标准,出差报销标准,住宿标准,住宿费标准,交通标准,餐饮标准,伙食标准,补贴标准"
            ),
            new BuiltinTerm(
                    "出差申请",
                    "员工因公外出或跨地区出差前需要提交和审批的流程。",
                    "出差申请,公出申请,因公外出,出差审批,公出单,出差申请单"
            )
    );

    private final KnowledgeTermRepository repository;
    private final DocumentPermissionService permissionService;

    public KnowledgeTermService(KnowledgeTermRepository repository,
                                DocumentPermissionService permissionService) {
        this.repository = repository;
        this.permissionService = permissionService;
    }

    public record TermRequest(
            String term,
            String definition,
            String synonyms,
            FileUpload.KnowledgeScope knowledgeScope,
            String departmentId,
            Boolean enabled
    ) {
    }

    public KnowledgeTerm create(String userId, TermRequest request) {
        User user = permissionService.requireUser(userId);
        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? FileUpload.KnowledgeScope.PUBLIC
                : request.knowledgeScope();
        String departmentId = normalizeDepartment(scope, request.departmentId(), user);
        ensureManagePermission(user, scope, departmentId);

        if (isBlank(request.term())) {
            throw new CustomException("术语名称不能为空", HttpStatus.BAD_REQUEST);
        }

        KnowledgeTerm term = new KnowledgeTerm();
        term.setTerm(request.term().trim());
        term.setDefinition(request.definition());
        term.setSynonyms(request.synonyms());
        term.setKnowledgeScope(scope);
        term.setDepartmentId(departmentId);
        term.setEnabled(request.enabled() == null || request.enabled());
        term.setCreatedBy(userId);
        return repository.save(term);
    }

    public KnowledgeTerm update(String userId, Long id, TermRequest request) {
        User user = permissionService.requireUser(userId);
        KnowledgeTerm term = repository.findById(id)
                .orElseThrow(() -> new CustomException("术语不存在", HttpStatus.NOT_FOUND));
        ensureManagePermission(user, term.getKnowledgeScope(), term.getDepartmentId());

        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? term.getKnowledgeScope()
                : request.knowledgeScope();
        String departmentId = normalizeDepartment(scope, request.departmentId(), user);
        ensureManagePermission(user, scope, departmentId);

        if (isBlank(request.term())) {
            throw new CustomException("术语名称不能为空", HttpStatus.BAD_REQUEST);
        }

        term.setTerm(request.term().trim());
        term.setDefinition(request.definition());
        term.setSynonyms(request.synonyms());
        term.setKnowledgeScope(scope);
        term.setDepartmentId(departmentId);
        if (request.enabled() != null) {
            term.setEnabled(request.enabled());
        }
        return repository.save(term);
    }

    public void delete(String userId, Long id) {
        User user = permissionService.requireUser(userId);
        KnowledgeTerm term = repository.findById(id)
                .orElseThrow(() -> new CustomException("术语不存在", HttpStatus.NOT_FOUND));
        ensureManagePermission(user, term.getKnowledgeScope(), term.getDepartmentId());
        repository.delete(term);
    }

    public List<KnowledgeTerm> listVisible(String userId) {
        User user = permissionService.requireUser(userId);
        return visibleTerms(user);
    }

    public List<KnowledgeTerm> listManageable(String userId) {
        User user = permissionService.requireUser(userId);
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(item -> canView(user, item.getKnowledgeScope(), item.getDepartmentId()))
                .toList();
    }

    public String expandQuery(String query, String userId) {
        List<KnowledgeTerm> matched = matchTerms(query, userId);
        List<BuiltinTerm> builtins = matchBuiltinTerms(query);
        if (matched.isEmpty() && builtins.isEmpty()) {
            return query;
        }
        StringBuilder builder = new StringBuilder(query).append("\n\n术语同义词提示：");
        for (KnowledgeTerm term : matched) {
            builder.append(term.getTerm()).append(" = ")
                    .append(String.join(" / ", labelsOf(term.getTerm(), term.getSynonyms())));
            if (!isBlank(term.getDefinition())) {
                builder.append("，定义：").append(term.getDefinition().trim());
            }
            builder.append("；");
        }
        for (BuiltinTerm term : builtins) {
            builder.append(term.term()).append(" = ")
                    .append(String.join(" / ", labelsOf(term.term(), term.synonyms())))
                    .append("，定义：").append(term.definition())
                    .append("；");
        }
        return builder.toString();
    }

    public String buildMatchedTermContext(String query, String userId) {
        List<KnowledgeTerm> matched = matchTerms(query, userId);
        List<BuiltinTerm> builtins = matchBuiltinTerms(query);
        if (matched.isEmpty() && builtins.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("【术语词典】\n");
        for (KnowledgeTerm term : matched) {
            builder.append("- ").append(term.getTerm());
            List<String> labels = labelsOf(term.getTerm(), term.getSynonyms());
            if (labels.size() > 1) {
                builder.append("（同义表达：").append(String.join("、", labels.subList(1, labels.size()))).append("）");
            }
            if (!isBlank(term.getDefinition())) {
                builder.append("：").append(term.getDefinition().trim());
            }
            builder.append("\n");
        }
        for (BuiltinTerm term : builtins) {
            List<String> labels = labelsOf(term.term(), term.synonyms());
            builder.append("- ").append(term.term());
            if (labels.size() > 1) {
                builder.append("（同义表达：").append(String.join("、", labels.subList(1, labels.size()))).append("）");
            }
            builder.append("：").append(term.definition()).append("\n");
        }
        return builder.toString();
    }

    private List<KnowledgeTerm> matchTerms(String query, String userId) {
        if (isBlank(query)) {
            return List.of();
        }
        User user = permissionService.requireUser(userId);
        String normalizedQuery = KnowledgeFaqService.normalizeText(query);
        return visibleTerms(user).stream()
                .filter(term -> labelsOf(term.getTerm(), term.getSynonyms()).stream()
                        .map(KnowledgeFaqService::normalizeText)
                        .anyMatch(label -> !label.isBlank() && normalizedQuery.contains(label)))
                .limit(8)
                .toList();
    }

    private List<BuiltinTerm> matchBuiltinTerms(String query) {
        if (isBlank(query)) {
            return List.of();
        }
        String normalizedQuery = KnowledgeFaqService.normalizeText(query);
        List<BuiltinTerm> result = new ArrayList<>();
        for (BuiltinTerm term : BUILTIN_TERMS) {
            boolean matched = labelsOf(term.term(), term.synonyms()).stream()
                    .map(KnowledgeFaqService::normalizeText)
                    .anyMatch(label -> !label.isBlank() && normalizedQuery.contains(label));
            if (matched) {
                result.add(term);
            }
        }
        return result;
    }

    private List<KnowledgeTerm> visibleTerms(User user) {
        return repository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .filter(item -> canView(user, item.getKnowledgeScope(), item.getDepartmentId()))
                .toList();
    }

    private void ensureManagePermission(User user, FileUpload.KnowledgeScope scope, String departmentId) {
        if (scope == FileUpload.KnowledgeScope.PUBLIC && !permissionService.canUploadPublic(user)) {
            throw new CustomException("没有管理公共术语的权限", HttpStatus.FORBIDDEN);
        }
        if (scope == FileUpload.KnowledgeScope.DEPARTMENT && !permissionService.canUploadDepartment(user, departmentId)) {
            throw new CustomException("没有管理该部门术语的权限", HttpStatus.FORBIDDEN);
        }
        if (scope == FileUpload.KnowledgeScope.PRIVATE) {
            throw new CustomException("暂不支持私人术语", HttpStatus.BAD_REQUEST);
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
                .distinct()
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record BuiltinTerm(String term, String definition, String synonyms) {
    }
}
