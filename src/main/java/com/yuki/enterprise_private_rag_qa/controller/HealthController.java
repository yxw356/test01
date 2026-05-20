package com.yuki.enterprise_private_rag_qa.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 根路径健康检查，便于在浏览器中确认后端已启动。
 * 本服务为 REST API，请通过前端 http://localhost:9527 访问界面。
 */
@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "enterprise-private-rag-qa",
                "api", "/api/v1",
                "frontend", "http://localhost:9527",
                "login", "POST /api/v1/users/login"
        );
    }
}
