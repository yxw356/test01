package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.CleaningRuleSet;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.CleaningRuleSetRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleaningRuleSetServiceTest {

    private CleaningRuleSetRepository repository;
    private CleaningRuleSetService service;

    @BeforeEach
    void setUp() {
        repository = mock(CleaningRuleSetRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrgTagCacheService orgTagCacheService = mock(OrgTagCacheService.class);
        DocumentPermissionService permissionService = new DocumentPermissionService(userRepository, orgTagCacheService);
        service = new CleaningRuleSetService(repository, permissionService);

        User admin = user(1L, "admin", User.Role.SUPER_ADMIN, "default,admin");
        User lead = user(2L, "lead", User.Role.DEPT_LEAD, "FIN");
        User member = user(3L, "member", User.Role.DEPT_MEMBER, "FIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(lead));
        when(userRepository.findById(3L)).thenReturn(Optional.of(member));
        when(orgTagCacheService.getUserEffectiveOrgTags("lead")).thenReturn(List.of("FIN"));
        when(orgTagCacheService.getUserEffectiveOrgTags("member")).thenReturn(List.of("FIN"));
    }

    @Test
    void superAdminCanCreatePublicRuleSet() {
        when(repository.save(any(CleaningRuleSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CleaningRuleSet ruleSet = service.createRuleSet("1", new CleaningRuleSetService.RuleSetRequest(
                "公共制度清洗", FileUpload.KnowledgeScope.PUBLIC, null, "去页脚和重复行",
                true, true, true, true, true, true, 8,
                List.of("^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$")
        ));

        assertEquals("公共制度清洗", ruleSet.getName());
        assertEquals(FileUpload.KnowledgeScope.PUBLIC, ruleSet.getKnowledgeScope());
        assertEquals("1", ruleSet.getCreatedBy());
        assertTrue(ruleSet.isEnabled());
        assertEquals("^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$", ruleSet.getDropLinePatterns());
        verify(repository).save(any(CleaningRuleSet.class));
    }

    @Test
    void departmentLeadCanCreateOwnDepartmentRuleSet() {
        when(repository.save(any(CleaningRuleSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CleaningRuleSet ruleSet = service.createRuleSet("2", new CleaningRuleSetService.RuleSetRequest(
                "财务文档清洗", FileUpload.KnowledgeScope.DEPARTMENT, "FIN", "",
                true, true, true, true, true, false, 8, List.of()
        ));

        assertEquals("FIN", ruleSet.getDepartmentId());
        assertEquals(FileUpload.KnowledgeScope.DEPARTMENT, ruleSet.getKnowledgeScope());
        assertEquals(false, ruleSet.isRemoveDuplicateLines());
    }

    @Test
    void departmentLeadCannotCreateOtherDepartmentRuleSet() {
        assertThrows(CustomException.class, () -> service.createRuleSet("2", new CleaningRuleSetService.RuleSetRequest(
                "运营文档清洗", FileUpload.KnowledgeScope.DEPARTMENT, "OPS", "",
                true, true, true, true, true, true, 8, List.of()
        )));
    }

    @Test
    void visibleRuleSetsIncludePublicAndOwnDepartment() {
        CleaningRuleSet publicRule = ruleSet(1L, "公共", FileUpload.KnowledgeScope.PUBLIC, null);
        CleaningRuleSet ownRule = ruleSet(2L, "财务", FileUpload.KnowledgeScope.DEPARTMENT, "FIN");
        CleaningRuleSet otherRule = ruleSet(3L, "运营", FileUpload.KnowledgeScope.DEPARTMENT, "OPS");
        when(repository.findByEnabledTrueOrderByUpdatedAtDesc()).thenReturn(List.of(publicRule, ownRule, otherRule));

        List<CleaningRuleSet> visible = service.listVisibleRuleSets("3");

        assertEquals(List.of(publicRule, ownRule), visible);
    }

    @Test
    void resolveRuleSetReturnsCleaningConfigForVisibleRuleSet() {
        CleaningRuleSet ruleSet = ruleSet(7L, "财务", FileUpload.KnowledgeScope.DEPARTMENT, "FIN");
        ruleSet.setRemoveDuplicateLines(false);
        ruleSet.setMinDuplicateLineLength(12);
        ruleSet.setDropLinePatterns("^内部资料$;;^第\\s*\\d+\\s*页$");
        when(repository.findByIdAndEnabledTrue(7L)).thenReturn(Optional.of(ruleSet));

        DataCleaningService.CleaningRuleConfig config = service.resolveRuleConfig(7L, "3");

        assertEquals(false, config.removeDuplicateLines());
        assertEquals(12, config.minDuplicateLineLength());
        assertEquals(List.of("^内部资料$", "^第\\s*\\d+\\s*页$"), config.dropLinePatterns());
    }

    @Test
    void departmentLeadCanUpdateOwnDepartmentRuleSet() {
        CleaningRuleSet ruleSet = ruleSet(7L, "旧名称", FileUpload.KnowledgeScope.DEPARTMENT, "FIN");
        when(repository.findByIdAndEnabledTrue(7L)).thenReturn(Optional.of(ruleSet));
        when(repository.save(any(CleaningRuleSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CleaningRuleSet updated = service.updateRuleSet("2", 7L, new CleaningRuleSetService.RuleSetRequest(
                "新名称", FileUpload.KnowledgeScope.DEPARTMENT, "FIN", "新版说明",
                true, true, false, true, false, false, 16, List.of("^页脚$")
        ));

        assertEquals("新名称", updated.getName());
        assertEquals("新版说明", updated.getDescription());
        assertEquals("FIN", updated.getDepartmentId());
        assertEquals(false, updated.isNormalizeWhitespace());
        assertEquals(false, updated.isCollapseBlankLines());
        assertEquals(false, updated.isRemoveDuplicateLines());
        assertEquals(16, updated.getMinDuplicateLineLength());
        assertEquals("^页脚$", updated.getDropLinePatterns());
        verify(repository).save(ruleSet);
    }

    @Test
    void departmentLeadCannotUpdateOtherDepartmentRuleSet() {
        CleaningRuleSet ruleSet = ruleSet(8L, "运营", FileUpload.KnowledgeScope.DEPARTMENT, "OPS");
        when(repository.findByIdAndEnabledTrue(8L)).thenReturn(Optional.of(ruleSet));

        assertThrows(CustomException.class, () -> service.updateRuleSet("2", 8L, new CleaningRuleSetService.RuleSetRequest(
                "新名称", FileUpload.KnowledgeScope.DEPARTMENT, "OPS", "",
                true, true, true, true, true, true, 8, List.of()
        )));
    }

    @Test
    void superAdminCanDisablePublicRuleSet() {
        CleaningRuleSet ruleSet = ruleSet(9L, "公共", FileUpload.KnowledgeScope.PUBLIC, null);
        when(repository.findByIdAndEnabledTrue(9L)).thenReturn(Optional.of(ruleSet));
        when(repository.save(any(CleaningRuleSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.disableRuleSet("1", 9L);

        assertEquals(false, ruleSet.isEnabled());
        verify(repository).save(ruleSet);
    }

    private CleaningRuleSet ruleSet(Long id, String name, FileUpload.KnowledgeScope scope, String departmentId) {
        CleaningRuleSet ruleSet = new CleaningRuleSet();
        ruleSet.setId(id);
        ruleSet.setName(name);
        ruleSet.setKnowledgeScope(scope);
        ruleSet.setDepartmentId(departmentId);
        ruleSet.setEnabled(true);
        ruleSet.setNormalizeLineBreaks(true);
        ruleSet.setNormalizeUnicodeSpaces(true);
        ruleSet.setNormalizeWhitespace(true);
        ruleSet.setTrimLines(true);
        ruleSet.setCollapseBlankLines(true);
        ruleSet.setRemoveDuplicateLines(true);
        ruleSet.setMinDuplicateLineLength(8);
        return ruleSet;
    }

    private User user(Long id, String username, User.Role role, String orgTags) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setOrgTags(orgTags);
        user.setPrimaryOrg(orgTags.split(",")[0]);
        return user;
    }
}
