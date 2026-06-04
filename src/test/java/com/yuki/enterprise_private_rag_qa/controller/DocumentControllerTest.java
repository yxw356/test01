package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.CleaningRuleSet;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.CleaningRuleSetRepository;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.service.AuditService;
import com.yuki.enterprise_private_rag_qa.service.DocumentIndexService;
import com.yuki.enterprise_private_rag_qa.service.DocumentPermissionService;
import com.yuki.enterprise_private_rag_qa.service.DocumentService;
import com.yuki.enterprise_private_rag_qa.service.OrgTagCacheService;
import com.yuki.enterprise_private_rag_qa.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentControllerTest {

    private DocumentController controller;
    private DocumentService documentService;
    private DocumentIndexService documentIndexService;
    private UserRepository userRepository;
    private OrgTagCacheService orgTagCacheService;
    private OrganizationTagRepository organizationTagRepository;
    private CleaningRuleSetRepository cleaningRuleSetRepository;

    @BeforeEach
    void setUp() {
        controller = new DocumentController();
        documentService = mock(DocumentService.class);
        userRepository = mock(UserRepository.class);
        orgTagCacheService = mock(OrgTagCacheService.class);
        organizationTagRepository = mock(OrganizationTagRepository.class);

        DocumentPermissionService permissionService = new DocumentPermissionService(userRepository, orgTagCacheService);

        ReflectionTestUtils.setField(controller, "documentService", documentService);
        ReflectionTestUtils.setField(controller, "fileUploadRepository", mock(FileUploadRepository.class));
        ReflectionTestUtils.setField(controller, "organizationTagRepository", organizationTagRepository);
        cleaningRuleSetRepository = mock(CleaningRuleSetRepository.class);
        ReflectionTestUtils.setField(controller, "cleaningRuleSetRepository", cleaningRuleSetRepository);
        ReflectionTestUtils.setField(controller, "jwtUtils", mock(JwtUtils.class));
        ReflectionTestUtils.setField(controller, "auditService", mock(AuditService.class));
        documentIndexService = mock(DocumentIndexService.class);
        ReflectionTestUtils.setField(controller, "documentIndexService", documentIndexService);
        ReflectionTestUtils.setField(controller, "documentPermissionService", permissionService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void accessibleDocumentsIncludeServerSidePermissionFlags() {
        User currentUser = user(1L, "member", User.Role.DEPT_MEMBER, "FIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(orgTagCacheService.getUserEffectiveOrgTags("member")).thenReturn(List.of("FIN"));
        when(organizationTagRepository.findByTagId("FIN")).thenReturn(Optional.empty());
        when(organizationTagRepository.findByTagId("OPS")).thenReturn(Optional.empty());

        FileUpload ownDepartmentDocument = document("1", "own.md", FileUpload.KnowledgeScope.DEPARTMENT, "FIN", false);
        ownDepartmentDocument.setCategoryId(9L);
        ownDepartmentDocument.setCategoryName("财务流程");
        ownDepartmentDocument.setCleaningStatus(FileUpload.CleaningStatus.CLEANED);
        ownDepartmentDocument.setOriginalChars(120);
        ownDepartmentDocument.setCleanedChars(100);
        ownDepartmentDocument.setRemovedChars(20);
        ownDepartmentDocument.setDuplicateLinesRemoved(2);
        ownDepartmentDocument.setCleaningQualityStatus(FileUpload.CleaningQualityStatus.WARNING);
        ownDepartmentDocument.setCleaningQualityIssues("REMOVED_RATIO_HIGH");
        ownDepartmentDocument.setCleaningQualityScore(0.65d);
        ownDepartmentDocument.setCleaningRuleSetId(7L);
        CleaningRuleSet cleaningRuleSet = new CleaningRuleSet();
        cleaningRuleSet.setId(7L);
        cleaningRuleSet.setName("财务清洗规则");
        FileUpload publicDocument = document("2", "public.md", FileUpload.KnowledgeScope.PUBLIC, "OPS", true);
        when(documentService.getAccessibleFiles("1", "FIN")).thenReturn(List.of(ownDepartmentDocument, publicDocument));
        when(cleaningRuleSetRepository.findAllById(any())).thenReturn(List.of(cleaningRuleSet));

        ResponseEntity<?> response = controller.getAccessibleFiles("1", "FIN");
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");

        assertEquals(200, body.get("code"));
        assertEquals(2, data.size());
        assertTrue((Boolean) data.get(0).get("canView"));
        assertTrue((Boolean) data.get(0).get("canManage"));
        assertEquals("DEPARTMENT", data.get(0).get("knowledgeScope"));
        assertEquals("FIN", data.get(0).get("departmentId"));
        assertEquals(9L, data.get(0).get("categoryId"));
        assertEquals("财务流程", data.get(0).get("categoryName"));
        assertEquals("CLEANED", data.get(0).get("cleaningStatus"));
        assertEquals(120, data.get(0).get("originalChars"));
        assertEquals(100, data.get(0).get("cleanedChars"));
        assertEquals(20, data.get(0).get("removedChars"));
        assertEquals(2, data.get(0).get("duplicateLinesRemoved"));
        assertEquals("WARNING", data.get(0).get("cleaningQualityStatus"));
        assertEquals("REMOVED_RATIO_HIGH", data.get(0).get("cleaningQualityIssues"));
        assertEquals(0.65d, (Double) data.get(0).get("cleaningQualityScore"), 0.001d);
        assertEquals(7L, data.get(0).get("cleaningRuleSetId"));
        assertEquals("财务清洗规则", data.get(0).get("cleaningRuleName"));

        assertTrue((Boolean) data.get(1).get("canView"));
        assertFalse((Boolean) data.get(1).get("canManage"));
        assertEquals("PUBLIC", data.get(1).get("knowledgeScope"));
        assertEquals("OPS", data.get(1).get("departmentId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryCleaningEndpointSubmitsCleaningAndIndexingTask() {
        ResponseEntity<?> response = controller.retryCleaning(
                "abc123abc123abc123abc123abc123ab",
                "99",
                "DEPT_LEAD"
        );

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(200, body.get("code"));
        assertEquals("清洗与索引任务已重新提交", body.get("message"));
        verify(documentIndexService).retryCleaningAndIndexing(
                "abc123abc123abc123abc123abc123ab",
                "99",
                "DEPT_LEAD"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryCleaningEndpointCanSwitchCleaningRuleSet() {
        ResponseEntity<?> response = controller.retryCleaning(
                "abc123abc123abc123abc123abc123ab",
                "99",
                "DEPT_LEAD",
                new DocumentController.RecleanRequest(12L)
        );

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(200, body.get("code"));
        verify(documentIndexService).retryCleaningAndIndexing(
                "abc123abc123abc123abc123abc123ab",
                "99",
                "DEPT_LEAD",
                12L
        );
    }

    private User user(Long id, String username, User.Role role, String orgTags) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setOrgTags(orgTags);
        return user;
    }

    private FileUpload document(String ownerId, String fileName, FileUpload.KnowledgeScope scope,
                                String departmentId, boolean isPublic) {
        FileUpload document = new FileUpload();
        document.setFileMd5(fileName + "-md5");
        document.setFileName(fileName);
        document.setTotalSize(128L);
        document.setStatus(1);
        document.setUserId(ownerId);
        document.setKnowledgeScope(scope);
        document.setDepartmentId(departmentId);
        document.setOrgTag(departmentId);
        document.setPublic(isPublic);
        return document;
    }
}
