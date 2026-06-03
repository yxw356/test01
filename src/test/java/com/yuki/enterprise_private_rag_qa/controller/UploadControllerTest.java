package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.config.KafkaConfig;
import com.yuki.enterprise_private_rag_qa.model.FileProcessingTask;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.service.AuditService;
import com.yuki.enterprise_private_rag_qa.service.DocumentPermissionService;
import com.yuki.enterprise_private_rag_qa.service.FileIndexStatusService;
import com.yuki.enterprise_private_rag_qa.service.FileTypeValidationService;
import com.yuki.enterprise_private_rag_qa.service.KnowledgeCategoryService;
import com.yuki.enterprise_private_rag_qa.service.OrgTagCacheService;
import com.yuki.enterprise_private_rag_qa.service.UploadService;
import com.yuki.enterprise_private_rag_qa.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

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
    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaConfig kafkaConfig;
    private FileUploadRepository fileUploadRepository;
    private FileIndexStatusService fileIndexStatusService;
    private UserRepository userRepository;
    private OrgTagCacheService orgTagCacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        uploadService = mock(UploadService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        kafkaConfig = mock(KafkaConfig.class);
        fileUploadRepository = mock(FileUploadRepository.class);
        fileIndexStatusService = mock(FileIndexStatusService.class);
        userRepository = mock(UserRepository.class);
        orgTagCacheService = mock(OrgTagCacheService.class);

        controller = new UploadController(uploadService, kafkaTemplate);
        DocumentPermissionService permissionService = new DocumentPermissionService(userRepository, orgTagCacheService);

        ReflectionTestUtils.setField(controller, "kafkaConfig", kafkaConfig);
        ReflectionTestUtils.setField(controller, "userService", mock(UserService.class));
        ReflectionTestUtils.setField(controller, "fileUploadRepository", fileUploadRepository);
        ReflectionTestUtils.setField(controller, "fileTypeValidationService", new FileTypeValidationService());
        ReflectionTestUtils.setField(controller, "auditService", mock(AuditService.class));
        ReflectionTestUtils.setField(controller, "fileIndexStatusService", fileIndexStatusService);
        ReflectionTestUtils.setField(controller, "documentPermissionService", permissionService);
        ReflectionTestUtils.setField(controller, "knowledgeCategoryService", mock(KnowledgeCategoryService.class));
        ReflectionTestUtils.setField(controller, "maxUploadFileSize", DataSize.ofMegabytes(200));

        when(kafkaConfig.getFileProcessingTopic()).thenReturn("file-processing");
        when(kafkaTemplate.executeInTransaction(any())).thenAnswer(invocation -> {
            KafkaOperations.OperationsCallback<String, Object, Object> callback = invocation.getArgument(0);
            return callback.doInOperations(kafkaTemplate);
        });
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
                any(), eq("FIN"), eq(true), eq("1"), eq(FileUpload.KnowledgeScope.PUBLIC), eq("FIN"),
                eq(null), eq(null), eq(null));
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
                any(), eq("FIN"), eq(false), eq("2"), eq(FileUpload.KnowledgeScope.DEPARTMENT), eq("FIN"),
                eq(null), eq(null), eq(null));
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
                any(), anyString(), anyBoolean(), anyString(), any(), anyString(), any(), any(), any());
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
                any(), anyString(), anyBoolean(), anyString(), any(), anyString(), any(), any(), any());
    }

    @Test
    void uploadRejectsFilesLargerThanConfiguredLimit() throws Exception {
        ReflectionTestUtils.setField(controller, "maxUploadFileSize", DataSize.ofMegabytes(1));

        MockMultipartFile file = new MockMultipartFile("file", "large.md", "text/markdown", "hello".getBytes());
        ResponseEntity<Map<String, Object>> response = controller.uploadChunk(
                "large-md5", 0, DataSize.ofMegabytes(2).toBytes(), "large.md", 1,
                "FIN", "PUBLIC", "FIN", null, null, true, file, "1"
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("文件大小超过限制，最大支持 1.00MB", response.getBody().get("message"));
        verify(uploadService, never()).uploadChunk(anyString(), anyInt(), anyLong(), anyString(),
                any(), anyString(), anyBoolean(), anyString(), any(), anyString(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeMarksFileAsCompletedBeforeSubmittingProcessingTask() {
        FileUpload file = new FileUpload();
        file.setFileMd5("merge-md5");
        file.setFileName("merge.md");
        file.setUserId("1");
        file.setOrgTag("DEFAULT");
        file.setDepartmentId("DEFAULT");
        file.setKnowledgeScope(FileUpload.KnowledgeScope.PUBLIC);
        file.setPublic(true);
        file.setStatus(0);

        when(fileUploadRepository.findByFileMd5AndUserId("merge-md5", "1")).thenReturn(Optional.of(file));
        when(uploadService.getUploadedChunks("merge-md5", "1")).thenReturn(List.of(0));
        when(uploadService.getTotalChunks("merge-md5", "1")).thenReturn(1);
        when(uploadService.mergeChunks("merge-md5", "merge.md", "1")).thenReturn("http://minio/merge.md");

        ResponseEntity<Map<String, Object>> response = controller.mergeFile(
                new UploadController.MergeRequest("merge-md5", "merge.md"),
                "1",
                new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, file.getStatus());
        verify(fileUploadRepository).save(file);
        verify(fileIndexStatusService).markPending("merge-md5", "1");

        ArgumentCaptor<Object> taskCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("file-processing"), taskCaptor.capture());
        FileProcessingTask task = (FileProcessingTask) taskCaptor.getValue();
        assertEquals("merge-md5", task.getFileMd5());
        assertEquals("http://minio/merge.md", task.getFilePath());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeIncludesSelectedCleaningRuleSetInProcessingTask() {
        FileUpload file = new FileUpload();
        file.setFileMd5("rule-merge-md5");
        file.setFileName("rule-merge.md");
        file.setUserId("1");
        file.setOrgTag("DEFAULT");
        file.setDepartmentId("DEFAULT");
        file.setKnowledgeScope(FileUpload.KnowledgeScope.PUBLIC);
        file.setPublic(true);
        file.setStatus(0);
        file.setCleaningRuleSetId(9L);

        when(fileUploadRepository.findByFileMd5AndUserId("rule-merge-md5", "1")).thenReturn(Optional.of(file));
        when(uploadService.getUploadedChunks("rule-merge-md5", "1")).thenReturn(List.of(0));
        when(uploadService.getTotalChunks("rule-merge-md5", "1")).thenReturn(1);
        when(uploadService.mergeChunks("rule-merge-md5", "rule-merge.md", "1")).thenReturn("http://minio/rule-merge.md");

        ResponseEntity<Map<String, Object>> response = controller.mergeFile(
                new UploadController.MergeRequest("rule-merge-md5", "rule-merge.md"),
                "1",
                new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<Object> taskCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("file-processing"), taskCaptor.capture());
        FileProcessingTask task = (FileProcessingTask) taskCaptor.getValue();
        assertEquals(9L, task.getCleaningRuleSetId());
    }

    private ResponseEntity<Map<String, Object>> uploadChunk(String fileMd5, String fileName, String scope,
                                                            String departmentId, boolean isPublic, String userId)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/markdown", "hello".getBytes());
        return controller.uploadChunk(fileMd5, 0, file.getSize(), fileName, 1, departmentId, scope, departmentId,
                null, null, isPublic, file, userId);
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
