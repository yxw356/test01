package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentPermissionServiceTest {

    private OrgTagCacheService orgTagCacheService;
    private DocumentPermissionService service;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        orgTagCacheService = mock(OrgTagCacheService.class);
        service = new DocumentPermissionService(userRepository, orgTagCacheService);
    }

    @Test
    void superAdminCanUploadPublicAndAnyDepartmentKnowledge() {
        User superAdmin = user(1L, "admin", User.Role.SUPER_ADMIN, "OPS");

        assertTrue(service.canUploadPublic(superAdmin));
        assertTrue(service.canUploadDepartment(superAdmin, "FIN"));
    }

    @Test
    void knowledgeAdminCanUploadPublicButCannotUploadDepartmentWithoutLeadRole() {
        User knowledgeAdmin = user(2L, "ka", User.Role.KNOWLEDGE_ADMIN, "FIN");
        when(orgTagCacheService.getUserEffectiveOrgTags("ka")).thenReturn(List.of("FIN"));

        assertTrue(service.canUploadPublic(knowledgeAdmin));
        assertFalse(service.canUploadDepartment(knowledgeAdmin, "FIN"));
    }

    @Test
    void departmentLeadCanUploadOwnDepartmentOnly() {
        User lead = user(3L, "lead", User.Role.DEPT_LEAD, "FIN,OPS");
        when(orgTagCacheService.getUserEffectiveOrgTags("lead")).thenReturn(List.of("FIN", "OPS"));

        assertTrue(service.canUploadDepartment(lead, "FIN"));
        assertTrue(service.canUploadDepartment(lead, "OPS"));
        assertFalse(service.canUploadDepartment(lead, "HR"));
    }

    @Test
    void normalMemberCannotUploadDepartmentKnowledge() {
        User member = user(4L, "member", User.Role.DEPT_MEMBER, "FIN");
        when(orgTagCacheService.getUserEffectiveOrgTags("member")).thenReturn(List.of("FIN"));

        assertFalse(service.canUploadDepartment(member, "FIN"));
        assertThrows(CustomException.class,
                () -> service.assertCanUpload(member, FileUpload.KnowledgeScope.DEPARTMENT, "FIN"));
    }

    @Test
    void publicKnowledgeCanBeViewedByAnyUser() {
        User user = user(5L, "user", User.Role.DEPT_MEMBER, "FIN");
        FileUpload document = document("9", FileUpload.KnowledgeScope.PUBLIC, "OPS", false);

        assertTrue(service.canView(user, document));
    }

    @Test
    void departmentKnowledgeCanBeViewedBySameDepartmentOnly() {
        User finUser = user(6L, "fin", User.Role.DEPT_MEMBER, "FIN");
        User opsUser = user(7L, "ops", User.Role.DEPT_MEMBER, "OPS");
        when(orgTagCacheService.getUserEffectiveOrgTags("fin")).thenReturn(List.of("FIN"));
        when(orgTagCacheService.getUserEffectiveOrgTags("ops")).thenReturn(List.of("OPS"));
        FileUpload document = document("9", FileUpload.KnowledgeScope.DEPARTMENT, "FIN", false);

        assertTrue(service.canView(finUser, document));
        assertFalse(service.canView(opsUser, document));
    }

    @Test
    void userOrgTagsAreMergedWhenCacheOnlyContainsDefaultTag() {
        User member = user(12L, "member", User.Role.DEPT_MEMBER, "QA_DEPT");
        when(orgTagCacheService.getUserEffectiveOrgTags("member")).thenReturn(List.of("DEFAULT"));
        FileUpload document = document("9", FileUpload.KnowledgeScope.DEPARTMENT, "QA_DEPT", false);

        assertTrue(service.canView(member, document));
    }

    @Test
    void departmentLeadCanManageDepartmentKnowledgeButMemberCannot() {
        User lead = user(8L, "lead", User.Role.DEPT_LEAD, "FIN");
        User member = user(9L, "member", User.Role.DEPT_MEMBER, "FIN");
        when(orgTagCacheService.getUserEffectiveOrgTags("lead")).thenReturn(List.of("FIN"));
        when(orgTagCacheService.getUserEffectiveOrgTags("member")).thenReturn(List.of("FIN"));
        FileUpload document = document("10", FileUpload.KnowledgeScope.DEPARTMENT, "FIN", false);

        assertTrue(service.canManage(lead, document));
        assertFalse(service.canManage(member, document));
    }

    @Test
    void ownerCanViewAndManageOwnPrivateKnowledge() {
        User owner = user(11L, "owner", User.Role.DEPT_MEMBER, "FIN");
        FileUpload document = document("11", FileUpload.KnowledgeScope.PRIVATE, "FIN", false);

        assertTrue(service.canView(owner, document));
        assertTrue(service.canManage(owner, document));
    }

    private User user(Long id, String username, User.Role role, String orgTags) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setOrgTags(orgTags);
        return user;
    }

    private FileUpload document(String ownerId, FileUpload.KnowledgeScope scope, String departmentId, boolean isPublic) {
        FileUpload document = new FileUpload();
        document.setUserId(ownerId);
        document.setKnowledgeScope(scope);
        document.setDepartmentId(departmentId);
        document.setOrgTag(departmentId);
        document.setPublic(isPublic);
        return document;
    }
}
