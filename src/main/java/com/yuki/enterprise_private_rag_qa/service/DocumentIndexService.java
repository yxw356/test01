package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.config.KafkaConfig;
import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.FileProcessingTask;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaConfig kafkaConfig;

    public DocumentIndexService(FileUploadRepository fileUploadRepository,
                                DocumentService documentService,
                                FileIndexStatusService fileIndexStatusService,
                                KafkaTemplate<String, Object> kafkaTemplate,
                                KafkaConfig kafkaConfig) {
        this.fileUploadRepository = fileUploadRepository;
        this.documentService = documentService;
        this.fileIndexStatusService = fileIndexStatusService;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaConfig = kafkaConfig;
    }

    @Transactional
    public void retryIndexing(String fileMd5, String requestUserId, String role) {
        FileUpload file = resolveFile(fileMd5, requestUserId, role);

        if (file.getStatus() != 1) {
            throw new CustomException("文件尚未上传完成，无法重新索引", HttpStatus.BAD_REQUEST);
        }

        if (!file.getUserId().equals(requestUserId) && !"ADMIN".equals(role)) {
            throw new CustomException("没有权限重新索引此文档", HttpStatus.FORBIDDEN);
        }

        String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new CustomException("无法生成文件访问地址，请确认 MinIO 中文件仍存在", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        FileProcessingTask task = new FileProcessingTask(
                file.getFileMd5(),
                downloadUrl,
                file.getFileName(),
                file.getUserId(),
                file.getOrgTag(),
                file.isPublic()
        );

        kafkaTemplate.executeInTransaction(kt -> {
            kt.send(kafkaConfig.getFileProcessingTopic(), task);
            return true;
        });

        fileIndexStatusService.markPending(file.getFileMd5(), file.getUserId());
        logger.info("已重新提交索引任务: fileMd5={}, fileName={}, operator={}", fileMd5, file.getFileName(), requestUserId);
    }

    private FileUpload resolveFile(String fileMd5, String requestUserId, String role) {
        if ("ADMIN".equals(role)) {
            return fileUploadRepository.findByFileMd5(fileMd5)
                    .orElseThrow(() -> new CustomException("文档不存在", HttpStatus.NOT_FOUND));
        }
        return fileUploadRepository.findByFileMd5AndUserId(fileMd5, requestUserId)
                .orElseThrow(() -> new CustomException("文档不存在", HttpStatus.NOT_FOUND));
    }
}
