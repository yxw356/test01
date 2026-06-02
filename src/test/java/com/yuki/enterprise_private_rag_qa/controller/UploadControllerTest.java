package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.config.KafkaConfig;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.service.AuditService;
import com.yuki.enterprise_private_rag_qa.service.DocumentPermissionService;
import com.yuki.enterprise_private_rag_qa.service.FileIndexStatusService;
import com.yuki.enterprise_private_rag_qa.service.FileTypeValidationService;
import com.yuki.enterprise_private_rag_qa.service.OrgTagCacheService;
import com.yuki.enterprise_private_rag_qa.service.UploadService;
import com.yuki.enterprise_private_rag_qa.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadControllerTest {

    private UploadController controller;
    private UploadService uploadService;
    private UserRepository userRepository;
    private OrgTagCacheService orgTagCacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        uploadService = mock(UploadService.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        userRepository = mock(UserRepository.class);
        orgTagCacheService = mock(OrgTagCacheService.class);

        controller = new UploadController(uploadService, kafkaTemplate);
        DocumentPermissionService permissionService = new DocumentPermissionService(userRepository, orgTagCacheService);

        ReflectionTestUtils.setField(controller, "kafkaConfig", mock(KafkaConfig.class));
        ReflectionTestUtils.setField(controller, "userService", mock(UserService.class));
        ReflectionTestUtils.setField(controller, "fileUploadRepository", mock(FileUploadRepository.class));
        ReflectionTestUtils.setField(controller, "fileTypeValidationService", new FileTypeValidationService());
        ReflectionTestUtils.setField(controller, "auditService", mock(AuditService.class));
        ReflectionTestUtils.setField(controller, "fileIndexStatusService", mock(FileIndexStatusService.class));
        ReflectionTestUtils.setField(controller, "documentPermissionService", permissionService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void knowledgeAdminCanUploadPublicKnowledge() throws Exception {
        User user = user(1L, "kb_admin", User.Role.KNOWLEDGE_ADMIN, "FIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(orgTagCacheService.getUserEffectiveOrgTags("kb_admin")).thenReturn(List.of("FIN"));
        when(uploadService.getUploadedChunks("public-md5", "1")).thenReturn(List.of(0));
        when(uploadService.getTotalChunks("public-md5", "1")).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = uploadChunk("public-md5", "public.md",
                "PUBLIC", "FIN", false, "1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().get("code"));
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(100.0, data.get("progress"));
        verify(uploadService).uploadChunk(eq("public-md5"), eq(0), anyLong(), eq("public.md"),
                any(), eq("FIN"), eq(true), eq("1"), eq(FileUpload.KnowledgeScope.PUBLIC), eq("FIN"));
    }

    @Test
    void departmentLeadCanUploadOwnDepartmentKnowledge() throws Exception {
        User user = user(2L, "dept_lead", User.Role.DEPT_LEAD, "FIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(orgTagCacheService.getUserEffectiveOrgTags("dept_lead")).thenReturn(List.of("FIN"));
        when(uploadService.getUploadedChunks("dept-md5", "2")).thenReturn(List.of(0));
        when(uploadService.getTotalChunks("dept-md5", "2")).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = uploadChunk("dept-md5", "dept.md",
                "DEPARTMENT", "FIN", false, "2");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(uploadService).uploadChunk(eq("dept-md5"), eq(0), anyLong(), eq("dept.md"),
                any(), eq("FIN"), eq(false), eq("2"), eq(FileUpload.KnowledgeScope.DEPARTMENT), eq("FIN"));
    }

    @Test
    void departmentLeadCannotUploadPublicKnowledge() throws Exception {
        User user = user(2L, "dept_lead", User.Role.DEPT_LEAD, "FIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(orgTagCacheService.getUserEffectiveOrgTags("dept_lead")).thenReturn(List.of("FIN"));

        ResponseEntity<Map<String, Object>> response = uploadChunk("forbidden-public-md5", "public.md",
                "PUBLIC", "FIN", false, "2");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Only super administrators can upload public knowledge", response.getBody().get("message"));
        verify(uploadService, never()).uploadChunk(anyString(), anyInt(), anyLong(), anyString(),
                any(), anyString(), anyBoolean(), anyString(), any(), anyString());
    }

    @Test
    void departmentMemberCannotUploadDepartmentKnowledge() throws Exception {
        User user = user(3L, "dept_member", User.Role.DEPT_MEMBER, "FIN");
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(orgTagCacheService.getUserEffectiveOrgTags("dept_member")).thenReturn(List.of("FIN"));

        ResponseEntity<Map<String, Object>> response = uploadChunk("forbidden-dept-md5", "dept.md",
                "DEPARTMENT", "FIN", false, "3");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Only department leads can upload department knowledge", response.getBody().get("message"));
        verify(uploadService, never()).uploadChunk(anyString(), anyInt(), anyLong(), anyString(),
                any(), anyString(), anyBoolean(), anyString(), any(), anyString());
    }

    private ResponseEntity<Map<String, Object>> uploadChunk(String fileMd5, String fileName, String scope,
                                                            String departmentId, boolean isPublic, String userId)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/markdown", "hello".getBytes());
        return controller.uploadChunk(fileMd5, 0, file.getSize(), fileName, 1, departmentId, scope, departmentId,
                isPublic, file, userId);
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
