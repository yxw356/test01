package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.config.KafkaConfig;
import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileProcessingTask;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档索引任务调度（重试入队）
 */
@Service
public class DocumentIndexService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIndexService.class);

    private final FileUploadRepository fileUploadRepository;
    private final DocumentService documentService;
    private final FileIndexStatusService fileIndexStatusService;
    private final DocumentPermissionService documentPermissionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaConfig kafkaConfig;

    public DocumentIndexService(FileUploadRepository fileUploadRepository,
                                DocumentService documentService,
                                FileIndexStatusService fileIndexStatusService,
                                DocumentPermissionService documentPermissionService,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                KafkaConfig kafkaConfig) {
        this.fileUploadRepository = fileUploadRepository;
        this.documentService = documentService;
        this.fileIndexStatusService = fileIndexStatusService;
        this.documentPermissionService = documentPermissionService;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaConfig = kafkaConfig;
    }

    @Transactional
    public void retryIndexing(String fileMd5, String requestUserId, String role) {
        FileUpload file = resolveCompletedManageableFile(fileMd5, requestUserId, "重新索引");
        FileProcessingTask task = buildProcessingTask(file);

        submitProcessingTask(task);
        fileIndexStatusService.markPending(file.getFileMd5(), file.getUserId());
        logger.info("已重新提交索引任务: fileMd5={}, fileName={}, operator={}", fileMd5, file.getFileName(), requestUserId);
    }

    @Transactional
    public void retryCleaningAndIndexing(String fileMd5, String requestUserId, String role) {
        retryCleaningAndIndexing(fileMd5, requestUserId, role, null);
    }

    @Transactional
    public void retryCleaningAndIndexing(String fileMd5, String requestUserId, String role, Long cleaningRuleSetId) {
        FileUpload file = resolveCompletedManageableFile(fileMd5, requestUserId, "重新清洗");

        if (cleaningRuleSetId != null) {
            file.setCleaningRuleSetId(cleaningRuleSetId);
        }
        file.setCleaningStatus(FileUpload.CleaningStatus.PENDING);
        file.setOriginalChars(0);
        file.setCleanedChars(0);
        file.setRemovedChars(0);
        file.setDuplicateLinesRemoved(0);
        file.setIndexStatus(FileIndexStatus.PENDING);
        file.setIndexError(null);
        fileUploadRepository.save(file);

        FileProcessingTask task = buildProcessingTask(file);
        submitProcessingTask(task);
        fileIndexStatusService.markPending(file.getFileMd5(), file.getUserId());
        logger.info("已重新提交清洗与索引任务: fileMd5={}, fileName={}, operator={}", fileMd5, file.getFileName(), requestUserId);
    }

    private FileProcessingTask buildProcessingTask(FileUpload file) {
        String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new CustomException("无法生成文件访问地址，请确认 MinIO 中文件仍存在", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new FileProcessingTask(
                file.getFileMd5(),
                downloadUrl,
                file.getFileName(),
                file.getUserId(),
                file.getOrgTag(),
                file.isPublic(),
                documentPermissionService.effectiveScope(file).name(),
                documentPermissionService.effectiveDepartmentId(file),
                file.getCategoryId(),
                file.getCategoryName(),
                file.getCleaningRuleSetId()
        );
    }

    private void submitProcessingTask(FileProcessingTask task) {
        kafkaTemplate.executeInTransaction(kt -> {
            kt.send(kafkaConfig.getFileProcessingTopic(), task);
            return true;
        });
    }

    private FileUpload resolveCompletedManageableFile(String fileMd5, String requestUserId, String actionName) {
        FileUpload file = resolveFile(fileMd5);

        if (file.getStatus() != 1) {
            throw new CustomException("文件尚未上传完成，无法" + actionName, HttpStatus.BAD_REQUEST);
        }

        User operator = documentPermissionService.requireUser(requestUserId);
        if (!documentPermissionService.canManage(operator, file)) {
            throw new CustomException("没有权限" + actionName + "此文档", HttpStatus.FORBIDDEN);
        }

        return file;
    }

    private FileUpload resolveFile(String fileMd5) {
        return fileUploadRepository.findByFileMd5(fileMd5)
                .orElseThrow(() -> new CustomException("文档不存在", HttpStatus.NOT_FOUND));
    }
}
