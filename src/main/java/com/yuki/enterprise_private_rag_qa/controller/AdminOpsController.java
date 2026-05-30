package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.model.AuditAction;
import com.yuki.enterprise_private_rag_qa.model.AuditLog;
import com.yuki.enterprise_private_rag_qa.service.AuditService;
import com.yuki.enterprise_private_rag_qa.service.MonitoringService;
import com.yuki.enterprise_private_rag_qa.utils.JwtUtils;
import com.yuki.enterprise_private_rag_qa.utils.LogUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOpsController {

    private final AuditService auditService;
    private final MonitoringService monitoringService;
    private final JwtUtils jwtUtils;

    public AdminOpsController(AuditService auditService,
                              MonitoringService monitoringService,
                              JwtUtils jwtUtils) {
        this.auditService = auditService;
        this.monitoringService = monitoringService;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> queryAuditLogs(
            @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        try {
            jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            Page<AuditLog> logs = auditService.query(
                    userId, action, from, to,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", logs.getContent(),
                    "total", logs.getTotalElements(),
                    "page", page,
                    "size", size
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_AUDIT_LOGS", "admin", "查询审计日志失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    @GetMapping("/monitoring/status")
    public ResponseEntity<?> monitoringStatus(@RequestHeader("Authorization") String token) {
        try {
            jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "success",
                    "data", monitoringService.collectStatus()
            ));
        } catch (Exception e) {
            LogUtils.logBusinessError("ADMIN_MONITORING", "admin", "查询运行监控失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", e.getMessage() != null ? e.getMessage() : "monitoring failed"));
        }
    }
}
