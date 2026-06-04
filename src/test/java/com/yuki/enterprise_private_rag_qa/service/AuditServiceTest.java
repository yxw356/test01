package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.AuditAction;
import com.yuki.enterprise_private_rag_qa.model.AuditLog;
import com.yuki.enterprise_private_rag_qa.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    @Test
    void recordSuccessPersistsDeviceFields() {
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditService service = new AuditService(repository);

        service.recordSuccess("1", "admin", AuditAction.LOGIN, "user", "admin",
                "login success", "203.0.113.10", 12L,
                "Mozilla/5.0 Chrome/126", "Desktop", "Chrome", "Windows");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertEquals("Mozilla/5.0 Chrome/126", saved.getUserAgent());
        assertEquals("Desktop", saved.getDeviceType());
        assertEquals("Chrome", saved.getBrowser());
        assertEquals("Windows", saved.getOs());
    }
}
