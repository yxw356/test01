package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Centralizes document lifecycle rules so search, indexing, and management views
 * do not each reinterpret effective/abolished/audit status differently.
 */
@Service
public class DocumentLifecycleService {

    private static final List<FileUpload.LifecycleStatus> SEARCHABLE_STATUSES = List.of(
            FileUpload.LifecycleStatus.ACTIVE,
            FileUpload.LifecycleStatus.APPROVED
    );

    public boolean isSearchable(FileUpload file) {
        return isSearchable(file, LocalDateTime.now());
    }

    public boolean isSearchable(FileUpload file, LocalDateTime now) {
        if (file == null) {
            return false;
        }
        if (!isAuditAccepted(file)) {
            return false;
        }
        if (!SEARCHABLE_STATUSES.contains(file.getLifecycleStatus())) {
            return false;
        }
        if (file.getEffectiveAt() != null && file.getEffectiveAt().isAfter(now)) {
            return false;
        }
        return file.getAbolishedAt() == null || file.getAbolishedAt().isAfter(now);
    }

    public boolean isAuditAccepted(FileUpload file) {
        if (file == null || file.getPolicyAuditStatus() == null) {
            return false;
        }
        return file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.NOT_REQUIRED
                || file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.PASS
                || file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.PASS_WITH_WARNINGS;
    }

    public boolean isVisibleInHistoryMode(FileUpload file) {
        return file != null && isAuditAccepted(file);
    }
}
