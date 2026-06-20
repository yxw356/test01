package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.config.KafkaConfig;
import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileProcessingTask;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;

@Service
public class AdminKnowledgeService {

    private final FileUploadRepository fileUploadRepository;
    private final DocumentService documentService;
    private final DocumentPermissionService documentPermissionService;
    private final KnowledgeSpaceService knowledgeSpaceService;
    private final FileIndexStatusService fileIndexStatusService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaConfig kafkaConfig;
    private final MinioClient minioClient;

    public AdminKnowledgeService(FileUploadRepository fileUploadRepository,
                                 DocumentService documentService,
                                 DocumentPermissionService documentPermissionService,
                                 KnowledgeSpaceService knowledgeSpaceService,
                                 FileIndexStatusService fileIndexStatusService,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 KafkaConfig kafkaConfig,
                                 MinioClient minioClient) {
        this.fileUploadRepository = fileUploadRepository;
        this.documentService = documentService;
        this.documentPermissionService = documentPermissionService;
        this.knowledgeSpaceService = knowledgeSpaceService;
        this.fileIndexStatusService = fileIndexStatusService;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaConfig = kafkaConfig;
        this.minioClient = minioClient;
    }

    @Transactional
    public FileUpload ingestPublicDocument(User admin, MultipartFile file, String description) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String fileName = file.getOriginalFilename() == null ? "admin-upload.bin" : file.getOriginalFilename();
        String fileMd5;
        try (InputStream inputStream = file.getInputStream()) {
            fileMd5 = DigestUtils.md5Hex(inputStream);
        }

        FileUpload document = fileUploadRepository.findByFileMd5(fileMd5).orElseGet(FileUpload::new);
        document.setFileMd5(fileMd5);
        document.setFileName(fileName);
        document.setTotalSize(file.getSize());
        document.setStatus(1);
        document.setIndexStatus(FileIndexStatus.PENDING);
        document.setUserId(String.valueOf(admin.getId()));
        document.setOrgTag("admin");
        document.setKnowledgeScope(FileUpload.KnowledgeScope.PUBLIC);
        document.setDepartmentId(null);
        document.setPublic(true);
        document.setCategoryName(description);
        document.setSpaceId(knowledgeSpaceService.ensureSpaceForDocument(document));
        document.setMergedAt(LocalDateTime.now());
        fileUploadRepository.save(document);

        try (InputStream uploadStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket("uploads")
                    .object("merged/" + fileName)
                    .stream(uploadStream, file.getSize(), -1)
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .build());
        }

        FileProcessingTask task = new FileProcessingTask(
                fileMd5,
                "merged/" + fileName,
                fileName,
                document.getUserId(),
                document.getOrgTag(),
                true,
                FileUpload.KnowledgeScope.PUBLIC.name(),
                null,
                document.getCategoryId(),
                description,
                document.getCleaningRuleSetId()
        );
        kafkaTemplate.send(kafkaConfig.getFileProcessingTopic(), task);
        fileIndexStatusService.markPending(fileMd5, document.getUserId());
        return document;
    }

    @Transactional
    public void deleteDocument(User admin, String fileMd5) {
        FileUpload document = fileUploadRepository.findByFileMd5(fileMd5)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        if (!documentPermissionService.canDelete(admin, document)) {
            throw new IllegalArgumentException("无权删除该文档");
        }
        documentService.deleteDocument(fileMd5, String.valueOf(admin.getId()));
    }
}
