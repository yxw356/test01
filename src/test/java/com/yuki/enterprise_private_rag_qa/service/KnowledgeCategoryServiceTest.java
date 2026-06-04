package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeCategory;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeCategoryRepository;
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

class KnowledgeCategoryServiceTest {

    private KnowledgeCategoryRepository categoryRepository;
    private DocumentPermissionService permissionService;
    private KnowledgeCategoryService service;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(KnowledgeCategoryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrgTagCacheService orgTagCacheService = mock(OrgTagCacheService.class);
        permissionService = new DocumentPermissionService(userRepository, orgTagCacheService);
        service = new KnowledgeCategoryService(categoryRepository, permissionService);

        User admin = user(1L, "admin", User.Role.SUPER_ADMIN, "FIN");
        User lead = user(2L, "lead", User.Role.DEPT_LEAD, "FIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(lead));
        when(orgTagCacheService.getUserEffectiveOrgTags("lead")).thenReturn(List.of("FIN"));
    }

    @Test
    void createPublicCategoryRequiresPublicUploadPermission() {
        when(categoryRepository.save(any(KnowledgeCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeCategory category = service.createCategory(
                "1", new KnowledgeCategoryService.CategoryCreateRequest(
                        "制度规范", null, FileUpload.KnowledgeScope.PUBLIC, null, "企业通用制度", 10
                )
        );

        assertEquals("制度规范", category.getName());
        assertEquals(FileUpload.KnowledgeScope.PUBLIC, category.getKnowledgeScope());
        assertTrue(category.isEnabled());
        verify(categoryRepository).save(any(KnowledgeCategory.class));
    }

    @Test
    void departmentLeadCanCreateOwnDepartmentCategory() {
        when(categoryRepository.save(any(KnowledgeCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeCategory category = service.createCategory(
                "2", new KnowledgeCategoryService.CategoryCreateRequest(
                        "销售培训", null, FileUpload.KnowledgeScope.DEPARTMENT, "FIN", "", 20
                )
        );

        assertEquals("FIN", category.getDepartmentId());
        assertEquals(FileUpload.KnowledgeScope.DEPARTMENT, category.getKnowledgeScope());
    }

    @Test
    void departmentLeadCannotCreateOtherDepartmentCategory() {
        assertThrows(CustomException.class, () -> service.createCategory(
                "2", new KnowledgeCategoryService.CategoryCreateRequest(
                        "其他部门", null, FileUpload.KnowledgeScope.DEPARTMENT, "OPS", "", 20
                )
        ));
    }

    @Test
    void visibleCategoriesIncludePublicAndOwnDepartment() {
        KnowledgeCategory publicCategory = category(1L, "公共制度", FileUpload.KnowledgeScope.PUBLIC, null);
        KnowledgeCategory ownDepartmentCategory = category(2L, "财务流程", FileUpload.KnowledgeScope.DEPARTMENT, "FIN");
        KnowledgeCategory otherDepartmentCategory = category(3L, "运营流程", FileUpload.KnowledgeScope.DEPARTMENT, "OPS");
        when(categoryRepository.findByEnabledTrueOrderBySortOrderAscNameAsc())
                .thenReturn(List.of(publicCategory, ownDepartmentCategory, otherDepartmentCategory));

        List<KnowledgeCategory> visible = service.listVisibleCategories("2");

        assertEquals(List.of(publicCategory, ownDepartmentCategory), visible);
    }

    @Test
    void uploadCategoryMustMatchScopeAndDepartment() {
        KnowledgeCategory category = category(7L, "财务流程", FileUpload.KnowledgeScope.DEPARTMENT, "FIN");
        when(categoryRepository.findByIdAndEnabledTrue(7L)).thenReturn(Optional.of(category));

        KnowledgeCategory resolved = service.resolveUploadCategory(
                7L, FileUpload.KnowledgeScope.DEPARTMENT, "FIN", permissionService.requireUser("2")
        );

        assertEquals(7L, resolved.getId());
        assertThrows(CustomException.class, () -> service.resolveUploadCategory(
                7L, FileUpload.KnowledgeScope.PUBLIC, null, permissionService.requireUser("2")
        ));
    }

    private KnowledgeCategory category(Long id, String name, FileUpload.KnowledgeScope scope, String departmentId) {
        KnowledgeCategory category = new KnowledgeCategory();
        category.setId(id);
        category.setName(name);
        category.setKnowledgeScope(scope);
        category.setDepartmentId(departmentId);
        category.setEnabled(true);
        return category;
    }

    private User user(Long id, String username, User.Role role, String orgTags) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setOrgTags(orgTags);
        return user;
    }
}
