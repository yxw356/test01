package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(length = 128)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(length = 32)
    private String result;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditLog(String userId, String username, AuditAction action, String resourceType,
                    String resourceId, String detail, String result, String clientIp, Long durationMs) {
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.detail = detail;
        this.result = result;
        this.clientIp = clientIp;
        this.durationMs = durationMs;
        this.createdAt = LocalDateTime.now();
    }
}
