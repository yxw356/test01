package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeCase;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeCaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class KnowledgeCaseService {

    private static final int POLICY_DRAFT_MIN_CASES = 3;

    private final KnowledgeCaseRepository repository;
    private final DocumentPermissionService permissionService;

    public KnowledgeCaseService(KnowledgeCaseRepository repository,
                                DocumentPermissionService permissionService) {
        this.repository = repository;
        this.permissionService = permissionService;
    }

    public record CaseRequest(
            String title,
            String scenario,
            String handling,
            String conclusion,
            String tags,
            FileUpload.KnowledgeScope knowledgeScope,
            String departmentId,
            KnowledgeCase.CaseStatus status,
            Boolean enabled
    ) {
    }

    public record PolicyDraftRequest(
            FileUpload.KnowledgeScope knowledgeScope,
            String departmentId,
            String title,
            String category
    ) {
    }

    public record PolicyDraftResult(
            String title,
            String knowledgeScope,
            String departmentId,
            int caseCount,
            List<Long> sourceCaseIds,
            String draft,
            LocalDateTime generatedAt
    ) {
    }

    public KnowledgeCase create(String userId, CaseRequest request) {
        User user = permissionService.requireUser(userId);
        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? FileUpload.KnowledgeScope.DEPARTMENT
                : request.knowledgeScope();
        String departmentId = normalizeDepartment(scope, request.departmentId(), user);
        ensureManagePermission(user, scope, departmentId);
        validate(request);

        KnowledgeCase item = new KnowledgeCase();
        apply(item, request, scope, departmentId);
        item.setCreatedBy(userId);
        return repository.save(item);
    }

    public KnowledgeCase update(String userId, Long id, CaseRequest request) {
        User user = permissionService.requireUser(userId);
        KnowledgeCase item = repository.findById(id)
                .orElseThrow(() -> new CustomException("案例不存在", HttpStatus.NOT_FOUND));
        ensureManagePermission(user, item.getKnowledgeScope(), item.getDepartmentId());

        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? item.getKnowledgeScope()
                : request.knowledgeScope();
        String departmentId = normalizeDepartment(scope, request.departmentId(), user);
        ensureManagePermission(user, scope, departmentId);
        validate(request);
        apply(item, request, scope, departmentId);
        return repository.save(item);
    }

    public void delete(String userId, Long id) {
        User user = permissionService.requireUser(userId);
        KnowledgeCase item = repository.findById(id)
                .orElseThrow(() -> new CustomException("案例不存在", HttpStatus.NOT_FOUND));
        ensureManagePermission(user, item.getKnowledgeScope(), item.getDepartmentId());
        repository.delete(item);
    }

    public List<KnowledgeCase> listVisible(String userId) {
        User user = permissionService.requireUser(userId);
        return repository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .filter(item -> item.getStatus() == KnowledgeCase.CaseStatus.APPROVED)
                .filter(item -> canView(user, item.getKnowledgeScope(), item.getDepartmentId()))
                .toList();
    }

    public List<KnowledgeCase> listManageable(String userId) {
        User user = permissionService.requireUser(userId);
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(item -> canView(user, item.getKnowledgeScope(), item.getDepartmentId()))
                .toList();
    }

    public String buildMatchedCaseContext(String query, String userId) {
        if (isBlank(query)) {
            return "";
        }
        String normalizedQuery = KnowledgeFaqService.normalizeText(query);
        List<KnowledgeCase> matched = listVisible(userId).stream()
                .filter(item -> labelsOf(item).stream()
                        .map(KnowledgeFaqService::normalizeText)
                        .anyMatch(label -> !label.isBlank() && normalizedQuery.contains(label)))
                .limit(5)
                .toList();
        if (matched.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("【案例参考】\n")
                .append("以下内容来自人工维护案例，只能作为辅助参考；如与制度文件冲突，以制度文件为准。\n");
        for (KnowledgeCase item : matched) {
            builder.append("- 案例#").append(item.getId()).append("：").append(item.getTitle()).append("\n");
            appendLine(builder, "场景", item.getScenario());
            appendLine(builder, "处理", item.getHandling());
            appendLine(builder, "结论", item.getConclusion());
        }
        return builder.toString();
    }

    public PolicyDraftResult generatePolicyDraft(String userId, PolicyDraftRequest request) {
        User user = permissionService.requireUser(userId);
        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? FileUpload.KnowledgeScope.DEPARTMENT
                : request.knowledgeScope();
        String departmentId = normalizeDepartment(scope, request.departmentId(), user);
        ensureManagePermission(user, scope, departmentId);

        List<KnowledgeCase> cases = repository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .filter(item -> item.getStatus() == KnowledgeCase.CaseStatus.APPROVED)
                .filter(item -> item.getKnowledgeScope() == scope)
                .filter(item -> scope == FileUpload.KnowledgeScope.PUBLIC || same(item.getDepartmentId(), departmentId))
                .limit(12)
                .toList();
        if (cases.size() < POLICY_DRAFT_MIN_CASES) {
            throw new CustomException("至少需要 3 条已审核启用案例，才能生成制度草案", HttpStatus.BAD_REQUEST);
        }

        String title = isBlank(request.title()) ? "案例沉淀制度草案" : request.title().trim();
        String draft = buildDraft(title, request.category(), scope, departmentId, cases);
        return new PolicyDraftResult(
                title,
                scope.name(),
                departmentId,
                cases.size(),
                cases.stream().map(KnowledgeCase::getId).toList(),
                draft,
                LocalDateTime.now()
        );
    }

    private void apply(KnowledgeCase item, CaseRequest request, FileUpload.KnowledgeScope scope, String departmentId) {
        item.setTitle(request.title().trim());
        item.setScenario(trim(request.scenario()));
        item.setHandling(trim(request.handling()));
        item.setConclusion(trim(request.conclusion()));
        item.setTags(trim(request.tags()));
        item.setKnowledgeScope(scope);
        item.setDepartmentId(departmentId);
        item.setStatus(request.status() == null ? KnowledgeCase.CaseStatus.DRAFT : request.status());
        item.setEnabled(request.enabled() == null || request.enabled());
    }

    private void validate(CaseRequest request) {
        if (request == null || isBlank(request.title())) {
            throw new CustomException("案例标题不能为空", HttpStatus.BAD_REQUEST);
        }
        if (isBlank(request.scenario()) || isBlank(request.handling()) || isBlank(request.conclusion())) {
            throw new CustomException("案例场景、处理过程和结论不能为空", HttpStatus.BAD_REQUEST);
        }
    }

    private String buildDraft(String title, String category, FileUpload.KnowledgeScope scope, String departmentId, List<KnowledgeCase> cases) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n");
        builder.append("## 一、目的\n");
        builder.append("基于已审核业务案例沉淀统一处理口径，减少同类问题重复判断和执行偏差。\n\n");
        builder.append("## 二、适用范围\n");
        builder.append(scope == FileUpload.KnowledgeScope.PUBLIC ? "适用于公司公共管理场景。\n\n" : "适用于部门：").append(scope == FileUpload.KnowledgeScope.PUBLIC ? "" : departmentId + "。\n\n");
        if (!isBlank(category)) {
            builder.append("制度分类：").append(category.trim()).append("。\n\n");
        }
        builder.append("## 三、典型场景\n");
        for (int i = 0; i < cases.size(); i++) {
            KnowledgeCase item = cases.get(i);
            builder.append(i + 1).append(". ").append(item.getTitle()).append("：").append(trimToOneLine(item.getScenario())).append("\n");
        }
        builder.append("\n## 四、处理流程\n");
        builder.append("1. 识别问题是否属于本制度适用范围。\n");
        builder.append("2. 按案例共性处理口径收集事实、责任主体和证据材料。\n");
        builder.append("3. 由部门负责人或授权岗位完成判断并留痕。\n");
        builder.append("4. 对例外情况提交上级复核，避免个人自由裁量扩大。\n\n");
        builder.append("## 五、处理口径\n");
        for (KnowledgeCase item : cases) {
            builder.append("- ").append(item.getTitle()).append("：").append(trimToOneLine(item.getConclusion())).append("\n");
        }
        builder.append("\n## 六、风险控制\n");
        builder.append("- 与现行制度文件冲突时，以正式制度文件为准。\n");
        builder.append("- 涉及金额、权限、处罚、劳动关系等高风险事项时，需要人工复核。\n");
        builder.append("- 本草案需经制度审核通过后才能纳入正式知识库。\n\n");
        builder.append("## 七、来源案例\n");
        for (KnowledgeCase item : cases) {
            builder.append("- 案例#").append(item.getId()).append("：").append(item.getTitle()).append("\n");
        }
        return builder.toString();
    }

    private void ensureManagePermission(User user, FileUpload.KnowledgeScope scope, String departmentId) {
        if (scope == FileUpload.KnowledgeScope.PUBLIC && !permissionService.canUploadPublic(user)) {
            throw new CustomException("没有管理公共案例的权限", HttpStatus.FORBIDDEN);
        }
        if (scope == FileUpload.KnowledgeScope.DEPARTMENT && !permissionService.canUploadDepartment(user, departmentId)) {
            throw new CustomException("没有管理该部门案例的权限", HttpStatus.FORBIDDEN);
        }
        if (scope == FileUpload.KnowledgeScope.PRIVATE) {
            throw new CustomException("暂不支持私人案例", HttpStatus.BAD_REQUEST);
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

    private List<String> labelsOf(KnowledgeCase item) {
        return Arrays.stream((item.getTitle() + "\n" + nvl(item.getTags()) + "\n" + nvl(item.getScenario())).split("[,，;；\\n]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void appendLine(StringBuilder builder, String label, String value) {
        if (!isBlank(value)) {
            builder.append("  ").append(label).append("：").append(trimToOneLine(value)).append("\n");
        }
    }

    private String trimToOneLine(String value) {
        String text = trim(value);
        return text.replaceAll("\\s+", " ");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
