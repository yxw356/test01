package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentLifecycleServiceTest {

    private final DocumentLifecycleService service = new DocumentLifecycleService();

    @Test
    void activeApprovedFileIsSearchable() {
        FileUpload file = file(FileUpload.LifecycleStatus.ACTIVE, FileUpload.PolicyAuditStatus.PASS);
        LocalDateTime now = LocalDateTime.now();
        file.setEffectiveAt(now.minusDays(1));
        file.setAbolishedAt(now.plusDays(1));

        assertTrue(service.isSearchable(file, now));
    }

    @Test
    void approvedFileBecomesSearchableAfterEffectiveTime() {
        FileUpload file = file(FileUpload.LifecycleStatus.APPROVED, FileUpload.PolicyAuditStatus.PASS);
        LocalDateTime now = LocalDateTime.now();
        file.setEffectiveAt(now.minusMinutes(1));

        assertTrue(service.isSearchable(file, now));
    }

    @Test
    void futureEffectiveFileIsNotSearchable() {
        FileUpload file = file(FileUpload.LifecycleStatus.ACTIVE, FileUpload.PolicyAuditStatus.PASS);
        LocalDateTime now = LocalDateTime.now();
        file.setEffectiveAt(now.plusHours(1));

        assertFalse(service.isSearchable(file, now));
    }

    @Test
    void expiredFileIsNotSearchable() {
        FileUpload file = file(FileUpload.LifecycleStatus.ACTIVE, FileUpload.PolicyAuditStatus.PASS);
        LocalDateTime now = LocalDateTime.now();
        file.setEffectiveAt(now.minusDays(10));
        file.setAbolishedAt(now.minusDays(1));

        assertFalse(service.isSearchable(file, now));
    }

    @Test
    void revokedFileIsNotSearchable() {
        FileUpload file = file(FileUpload.LifecycleStatus.REVOKED, FileUpload.PolicyAuditStatus.PASS);
        file.setEffectiveAt(LocalDateTime.now().minusDays(1));

        assertFalse(service.isSearchable(file, LocalDateTime.now()));
    }

    @Test
    void auditRejectedFileIsNotSearchable() {
        FileUpload file = file(FileUpload.LifecycleStatus.ACTIVE, FileUpload.PolicyAuditStatus.REJECT);
        file.setEffectiveAt(LocalDateTime.now().minusDays(1));

        assertFalse(service.isSearchable(file, LocalDateTime.now()));
    }

    @Test
    void manualReviewFileIsNotSearchable() {
        FileUpload file = file(FileUpload.LifecycleStatus.ACTIVE, FileUpload.PolicyAuditStatus.NEED_MANUAL_REVIEW);
        file.setEffectiveAt(LocalDateTime.now().minusDays(1));

        assertFalse(service.isSearchable(file, LocalDateTime.now()));
    }

    @Test
    void passWithWarningsFileIsSearchable() {
        FileUpload file = file(FileUpload.LifecycleStatus.ACTIVE, FileUpload.PolicyAuditStatus.PASS_WITH_WARNINGS);
        file.setEffectiveAt(LocalDateTime.now().minusDays(1));

        assertTrue(service.isSearchable(file, LocalDateTime.now()));
    }

    @Test
    void historicalQueryCanIncludeExpiredAcceptedFile() {
        FileUpload file = file(FileUpload.LifecycleStatus.EXPIRED, FileUpload.PolicyAuditStatus.PASS);
        LocalDateTime now = LocalDateTime.now();
        file.setEffectiveAt(now.minusYears(2));
        file.setAbolishedAt(now.minusYears(1));

        assertTrue(service.isVisibleInHistoryMode(file));
    }

    @Test
    void historicalQueryCannotIncludeRejectedFile() {
        FileUpload file = file(FileUpload.LifecycleStatus.EXPIRED, FileUpload.PolicyAuditStatus.REJECT);

        assertFalse(service.isVisibleInHistoryMode(file));
    }

    private FileUpload file(FileUpload.LifecycleStatus lifecycleStatus, FileUpload.PolicyAuditStatus auditStatus) {
        FileUpload file = new FileUpload();
        file.setFileMd5("0123456789abcdef0123456789abcdef");
        file.setFileName("制度文件.md");
        file.setLifecycleStatus(lifecycleStatus);
        file.setPolicyAuditStatus(auditStatus);
        return file;
    }
}
