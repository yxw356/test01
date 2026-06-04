package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.CleaningRuleSet;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.CleaningRuleSetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CleaningRuleSetService {

    private static final String PATTERN_DELIMITER = ";;";

    private final CleaningRuleSetRepository repository;
    private final DocumentPermissionService permissionService;

    public CleaningRuleSetService(CleaningRuleSetRepository repository,
                                  DocumentPermissionService permissionService) {
        this.repository = repository;
        this.permissionService = permissionService;
    }

    public record RuleSetRequest(
            String name,
            FileUpload.KnowledgeScope knowledgeScope,
            String departmentId,
            String description,
            Boolean normalizeLineBreaks,
            Boolean normalizeUnicodeSpaces,
            Boolean normalizeWhitespace,
            Boolean trimLines,
            Boolean collapseBlankLines,
            Boolean removeDuplicateLines,
            Integer minDuplicateLineLength,
            List<String> dropLinePatterns
    ) {
    }

    public CleaningRuleSet createRuleSet(String userId, RuleSetRequest request) {
        User user = permissionService.requireUser(userId);
        RuleScope ruleScope = resolveWritableScope(user, request, "创建");

        if (request.name() == null || request.name().isBlank()) {
            throw new CustomException("规则集名称不能为空", HttpStatus.BAD_REQUEST);
        }

        CleaningRuleSet ruleSet = new CleaningRuleSet();
        ruleSet.setName(request.name().trim());
        ruleSet.setKnowledgeScope(ruleScope.scope());
        ruleSet.setDepartmentId(ruleScope.departmentId());
        ruleSet.setDescription(request.description());
        applyRuleConfig(ruleSet, request);
        ruleSet.setCreatedBy(userId);
        ruleSet.setEnabled(true);
        return repository.save(ruleSet);
    }

    public CleaningRuleSet updateRuleSet(String userId, Long ruleSetId, RuleSetRequest request) {
        CleaningRuleSet ruleSet = repository.findByIdAndEnabledTrue(ruleSetId)
                .orElseThrow(() -> new CustomException("清洗规则集不存在或已停用", HttpStatus.NOT_FOUND));
        User user = permissionService.requireUser(userId);
        assertCanManageRuleSet(user, ruleSet);
        RuleScope ruleScope = resolveWritableScope(user, request, "更新");

        if (request.name() == null || request.name().isBlank()) {
            throw new CustomException("规则集名称不能为空", HttpStatus.BAD_REQUEST);
        }

        ruleSet.setName(request.name().trim());
        ruleSet.setKnowledgeScope(ruleScope.scope());
        ruleSet.setDepartmentId(ruleScope.departmentId());
        ruleSet.setDescription(request.description());
        applyRuleConfig(ruleSet, request);
        return repository.save(ruleSet);
    }

    public void disableRuleSet(String userId, Long ruleSetId) {
        CleaningRuleSet ruleSet = repository.findByIdAndEnabledTrue(ruleSetId)
                .orElseThrow(() -> new CustomException("清洗规则集不存在或已停用", HttpStatus.NOT_FOUND));
        User user = permissionService.requireUser(userId);
        assertCanManageRuleSet(user, ruleSet);
        ruleSet.setEnabled(false);
        repository.save(ruleSet);
    }

    public List<CleaningRuleSet> listVisibleRuleSets(String userId) {
        User user = permissionService.requireUser(userId);
        return repository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
                .filter(ruleSet -> canViewRuleSet(user, ruleSet))
                .toList();
    }

    public DataCleaningService.CleaningRuleConfig resolveRuleConfig(Long ruleSetId, String userId) {
        CleaningRuleSet ruleSet = repository.findByIdAndEnabledTrue(ruleSetId)
                .orElseThrow(() -> new CustomException("清洗规则集不存在或已停用", HttpStatus.BAD_REQUEST));
        User user = permissionService.requireUser(userId);
        if (!canViewRuleSet(user, ruleSet)) {
            throw new CustomException("没有使用该清洗规则集的权限", HttpStatus.FORBIDDEN);
        }
        return toCleaningRuleConfig(ruleSet);
    }

    public DataCleaningService.CleaningRuleConfig toCleaningRuleConfig(CleaningRuleSet ruleSet) {
        return new DataCleaningService.CleaningRuleConfig(
                ruleSet.isNormalizeLineBreaks(),
                ruleSet.isNormalizeUnicodeSpaces(),
                ruleSet.isNormalizeWhitespace(),
                ruleSet.isTrimLines(),
                ruleSet.isCollapseBlankLines(),
                ruleSet.isRemoveDuplicateLines(),
                ruleSet.getMinDuplicateLineLength(),
                parsePatterns(ruleSet.getDropLinePatterns())
        ).normalized();
    }

    public boolean canViewRuleSet(User user, CleaningRuleSet ruleSet) {
        if (user == null || ruleSet == null || !ruleSet.isEnabled()) {
            return false;
        }
        if (ruleSet.getKnowledgeScope() == FileUpload.KnowledgeScope.PUBLIC) {
            return true;
        }
        if (ruleSet.getKnowledgeScope() == FileUpload.KnowledgeScope.DEPARTMENT) {
            FileUpload file = new FileUpload();
            file.setKnowledgeScope(FileUpload.KnowledgeScope.DEPARTMENT);
            file.setDepartmentId(ruleSet.getDepartmentId());
            file.setOrgTag(ruleSet.getDepartmentId());
            return permissionService.canView(user, file);
        }
        return false;
    }

    private void assertCanManageRuleSet(User user, CleaningRuleSet ruleSet) {
        if (ruleSet.getKnowledgeScope() == FileUpload.KnowledgeScope.PUBLIC) {
            if (!permissionService.canUploadPublic(user)) {
                throw new CustomException("没有管理公共清洗规则集的权限", HttpStatus.FORBIDDEN);
            }
            return;
        }
        if (ruleSet.getKnowledgeScope() == FileUpload.KnowledgeScope.DEPARTMENT) {
            if (!permissionService.canUploadDepartment(user, ruleSet.getDepartmentId())) {
                throw new CustomException("没有管理该部门清洗规则集的权限", HttpStatus.FORBIDDEN);
            }
            return;
        }
        throw new CustomException("暂不支持管理私人清洗规则集", HttpStatus.BAD_REQUEST);
    }

    private RuleScope resolveWritableScope(User user, RuleSetRequest request, String actionName) {
        FileUpload.KnowledgeScope scope = request.knowledgeScope() == null
                ? FileUpload.KnowledgeScope.DEPARTMENT
                : request.knowledgeScope();
        String departmentId = request.departmentId();

        if (scope == FileUpload.KnowledgeScope.PUBLIC) {
            if (!permissionService.canUploadPublic(user)) {
                throw new CustomException("没有" + actionName + "公共清洗规则集的权限", HttpStatus.FORBIDDEN);
            }
            return new RuleScope(scope, null);
        }
        if (scope == FileUpload.KnowledgeScope.DEPARTMENT) {
            if (isBlank(departmentId)) {
                departmentId = user.getPrimaryOrg() != null ? user.getPrimaryOrg() : firstOrgTag(user);
            }
            if (!permissionService.canUploadDepartment(user, departmentId)) {
                throw new CustomException("没有" + actionName + "该部门清洗规则集的权限", HttpStatus.FORBIDDEN);
            }
            return new RuleScope(scope, departmentId);
        }
        throw new CustomException("暂不支持" + actionName + "私人清洗规则集", HttpStatus.BAD_REQUEST);
    }

    private void applyRuleConfig(CleaningRuleSet ruleSet, RuleSetRequest request) {
        DataCleaningService.CleaningRuleConfig defaults = DataCleaningService.CleaningRuleConfig.defaultConfig();
        ruleSet.setNormalizeLineBreaks(valueOrDefault(request.normalizeLineBreaks(), defaults.normalizeLineBreaks()));
        ruleSet.setNormalizeUnicodeSpaces(valueOrDefault(request.normalizeUnicodeSpaces(), defaults.normalizeUnicodeSpaces()));
        ruleSet.setNormalizeWhitespace(valueOrDefault(request.normalizeWhitespace(), defaults.normalizeWhitespace()));
        ruleSet.setTrimLines(valueOrDefault(request.trimLines(), defaults.trimLines()));
        ruleSet.setCollapseBlankLines(valueOrDefault(request.collapseBlankLines(), defaults.collapseBlankLines()));
        ruleSet.setRemoveDuplicateLines(valueOrDefault(request.removeDuplicateLines(), defaults.removeDuplicateLines()));
        ruleSet.setMinDuplicateLineLength(Math.max(1, request.minDuplicateLineLength() == null
                ? defaults.minDuplicateLineLength()
                : request.minDuplicateLineLength()));
        ruleSet.setDropLinePatterns(serializePatterns(request.dropLinePatterns()));
    }

    private boolean valueOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private String serializePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return null;
        }
        String value = String.join(PATTERN_DELIMITER, patterns.stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .map(String::trim)
                .toList());
        return value.isBlank() ? null : value;
    }

    private List<String> parsePatterns(String patterns) {
        if (patterns == null || patterns.isBlank()) {
            return List.of();
        }
        return List.of(patterns.split("\\s*" + PatternEscapes.DELIMITER_REGEX + "\\s*")).stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstOrgTag(User user) {
        String orgTags = user.getOrgTags();
        if (orgTags == null || orgTags.isBlank()) {
            return null;
        }
        return orgTags.split(",")[0].trim();
    }

    private static final class PatternEscapes {
        private static final String DELIMITER_REGEX = "\\Q" + PATTERN_DELIMITER + "\\E";
    }

    private record RuleScope(FileUpload.KnowledgeScope scope, String departmentId) {
    }
}
