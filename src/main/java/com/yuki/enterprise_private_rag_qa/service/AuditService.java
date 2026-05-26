package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.AuditAction;
import com.yuki.enterprise_private_rag_qa.model.AuditLog;
import com.yuki.enterprise_private_rag_qa.repository.AuditLogRepository;
import com.yuki.enterprise_private_rag_qa.utils.AuditSupport;
import com.yuki.enterprise_private_rag_qa.utils.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final int DETAIL_MAX = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userId, String username, AuditAction action, String resourceType,
                       String resourceId, String detail, String result, String clientIp, Long durationMs) {
        String safeDetail = AuditSupport.truncate(detail, DETAIL_MAX);
        try {
            auditLogRepository.save(new AuditLog(
                    userId, username, action, resourceType, resourceId,
                    safeDetail, result, clientIp, durationMs
            ));
            LogUtils.logUserOperation(userId != null ? userId : "system", action.name(),
                    resourceId != null ? resourceId : resourceType, result);
        } catch (Exception e) {
            logger.error("写入审计日志失败: action={}, userId={}", action, userId, e);
        }
    }

    public void recordSuccess(String userId, String username, AuditAction action, String resourceType,
                              String resourceId, String detail, String clientIp, Long durationMs) {
        record(userId, username, action, resourceType, resourceId, detail, "SUCCESS", clientIp, durationMs);
    }

    public void recordFailure(String userId, String username, AuditAction action, String resourceType,
                              String resourceId, String detail, String clientIp, Long durationMs) {
        record(userId, username, action, resourceType, resourceId, detail, "FAILURE", clientIp, durationMs);
    }

    public Page<AuditLog> query(String userId, AuditAction action, LocalDateTime from,
                                LocalDateTime to, Pageable pageable) {
        return auditLogRepository.search(userId, action, from, to, pageable);
    }
}
