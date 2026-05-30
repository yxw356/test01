package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileIndexStatusService {

    private static final Logger logger = LoggerFactory.getLogger(FileIndexStatusService.class);

    private final FileUploadRepository fileUploadRepository;

    public FileIndexStatusService(FileUploadRepository fileUploadRepository) {
        this.fileUploadRepository = fileUploadRepository;
    }

    @Transactional
    public void markPending(String fileMd5, String userId) {
        updateStatus(fileMd5, userId, FileIndexStatus.PENDING, null);
    }

    @Transactional
    public void markIndexing(String fileMd5, String userId) {
        updateStatus(fileMd5, userId, FileIndexStatus.INDEXING, null);
    }

    @Transactional
    public void markIndexed(String fileMd5, String userId) {
        updateStatus(fileMd5, userId, FileIndexStatus.INDEXED, null);
    }

    @Transactional
    public void markFailed(String fileMd5, String userId, String error) {
        updateStatus(fileMd5, userId, FileIndexStatus.FAILED, truncate(error, 500));
    }

    private void updateStatus(String fileMd5, String userId, int indexStatus, String indexError) {
        fileUploadRepository.findByFileMd5AndUserId(fileMd5, userId).ifPresentOrElse(file -> {
            file.setIndexStatus(indexStatus);
            file.setIndexError(indexError);
            fileUploadRepository.save(file);
            logger.info("文件索引状态更新: fileMd5={}, userId={}, indexStatus={}", fileMd5, userId, indexStatus);
        }, () -> logger.warn("未找到文件记录，跳过索引状态更新: fileMd5={}, userId={}", fileMd5, userId));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
