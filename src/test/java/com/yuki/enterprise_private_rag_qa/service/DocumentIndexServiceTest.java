package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.config.KafkaConfig;
import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileProcessingTask;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentIndexServiceTest {

    private FileUploadRepository fileUploadRepository;
    private DocumentService documentService;
    private FileIndexStatusService fileIndexStatusService;
    private DocumentPermissionService documentPermissionService;
    private DocumentLifecycleService documentLifecycleService;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaConfig kafkaConfig;
    private DocumentIndexService service;

    @BeforeEach
    void setUp() {
        fileUploadRepository = mock(FileUploadRepository.class);
        documentService = mock(DocumentService.class);
        fileIndexStatusService = mock(FileIndexStatusService.class);
        documentPermissionService = mock(DocumentPermissionService.class);
        documentLifecycleService = new DocumentLifecycleService();
        kafkaTemplate = mock(KafkaTemplate.class);
        kafkaConfig = mock(KafkaConfig.class);
        service = new DocumentIndexService(
                fileUploadRepository,
                documentService,
                fileIndexStatusService,
                documentPermissionService,
                documentLifecycleService,
                kafkaTemplate,
                kafkaConfig
        );

        when(kafkaConfig.getFileProcessingTopic()).thenReturn("file-processing");
        when(kafkaTemplate.executeInTransaction(any())).thenAnswer(invocation -> {
            KafkaOperations.OperationsCallback<String, Object, Object> callback = invocation.getArgument(0);
            return callback.doInOperations(kafkaTemplate);
        });
    }

    @Test
    void retryCleaningAndIndexingResetsCleaningStatsAndSubmitsProcessingTask() {
        FileUpload file = completedFile();
        User operator = new User();
        operator.setId(99L);
        operator.setUsername("lead");
        operator.setRole(User.Role.DEPT_LEAD);

        when(fileUploadRepository.findByFileMd5("abc123abc123abc123abc123abc123ab")).thenReturn(Optional.of(file));
        when(documentPermissionService.requireUser("99")).thenReturn(operator);
        when(documentPermissionService.canReclean(operator, file)).thenReturn(true);
        when(documentPermissionService.effectiveScope(file)).thenReturn(FileUpload.KnowledgeScope.DEPARTMENT);
        when(documentPermissionService.effectiveDepartmentId(file)).thenReturn("FIN");
        when(documentService.generateDownloadUrl(file.getFileMd5())).thenReturn("http://minio/download-url");

        service.retryCleaningAndIndexing(file.getFileMd5(), "99", "DEPT_LEAD");

        ArgumentCaptor<FileProcessingTask> taskCaptor = ArgumentCaptor.forClass(FileProcessingTask.class);
        verify(kafkaTemplate).send(eq("file-processing"), taskCaptor.capture());
        FileProcessingTask task = taskCaptor.getValue();
        assertEquals(file.getFileMd5(), task.getFileMd5());
        assertEquals("http://minio/download-url", task.getFilePath());
        assertEquals("file.md", task.getFileName());
        assertEquals("7", task.getUserId());
        assertEquals("FIN", task.getOrgTag());
        assertEquals("DEPARTMENT", task.getKnowledgeScope());
        assertEquals("FIN", task.getDepartmentId());
        assertEquals(3L, task.getCategoryId());
        assertEquals("制度流程", task.getCategoryName());

        assertEquals(FileUpload.CleaningStatus.PENDING, file.getCleaningStatus());
        assertEquals(0, file.getOriginalChars());
        assertEquals(0, file.getCleanedChars());
        assertEquals(0, file.getRemovedChars());
        assertEquals(0, file.getDuplicateLinesRemoved());
        assertEquals(FileIndexStatus.PENDING, file.getIndexStatus());
        assertNull(file.getIndexError());
        verify(fileUploadRepository).save(file);
        verify(fileIndexStatusService).markPending(file.getFileMd5(), file.getUserId());
    }

    @Test
    void retryIndexingRejectsAuditRejectedFile() {
        FileUpload file = completedFile();
        file.setPolicyAuditStatus(FileUpload.PolicyAuditStatus.REJECT);
        file.setLifecycleStatus(FileUpload.LifecycleStatus.AUDIT_REJECTED);
        User operator = operator();

        when(fileUploadRepository.findByFileMd5(file.getFileMd5())).thenReturn(Optional.of(file));
        when(documentPermissionService.requireUser("99")).thenReturn(operator);
        when(documentPermissionService.canReindex(operator, file)).thenReturn(true);

        CustomException error = assertThrows(CustomException.class,
                () -> service.retryIndexing(file.getFileMd5(), "99", "DEPT_LEAD"));

        assertEquals("文件未生效、已废止或审计未通过，不能纳入知识库检索", error.getMessage());
        verify(kafkaTemplate, never()).send(any(), any());
        verify(fileIndexStatusService, never()).markPending(any(), any());
    }

    @Test
    void retryCleaningAndIndexingRejectsExpiredFile() {
        FileUpload file = completedFile();
        file.setAbolishedAt(LocalDateTime.now().minusDays(1));
        file.setLifecycleStatus(FileUpload.LifecycleStatus.EXPIRED);
        User operator = operator();

        when(fileUploadRepository.findByFileMd5(file.getFileMd5())).thenReturn(Optional.of(file));
        when(documentPermissionService.requireUser("99")).thenReturn(operator);
        when(documentPermissionService.canReclean(operator, file)).thenReturn(true);

        CustomException error = assertThrows(CustomException.class,
                () -> service.retryCleaningAndIndexing(file.getFileMd5(), "99", "DEPT_LEAD"));

        assertEquals("文件未生效、已废止或审计未通过，不能纳入知识库检索", error.getMessage());
        verify(fileUploadRepository, never()).save(file);
        verify(kafkaTemplate, never()).send(any(), any());
        verify(fileIndexStatusService, never()).markPending(any(), any());
    }

    private FileUpload completedFile() {
        FileUpload file = new FileUpload();
        file.setFileMd5("abc123abc123abc123abc123abc123ab");
        file.setFileName("file.md");
        file.setStatus(1);
        file.setUserId("7");
        file.setOrgTag("FIN");
        file.setDepartmentId("FIN");
        file.setKnowledgeScope(FileUpload.KnowledgeScope.DEPARTMENT);
        file.setCategoryId(3L);
        file.setCategoryName("制度流程");
        file.setCleaningStatus(FileUpload.CleaningStatus.CLEANED);
        file.setOriginalChars(100);
        file.setCleanedChars(80);
        file.setRemovedChars(20);
        file.setDuplicateLinesRemoved(2);
        file.setIndexStatus(FileIndexStatus.FAILED);
        file.setIndexError("old error");
        return file;
    }

    private User operator() {
        User operator = new User();
        operator.setId(99L);
        operator.setUsername("lead");
        operator.setRole(User.Role.DEPT_LEAD);
        return operator;
    }
}
