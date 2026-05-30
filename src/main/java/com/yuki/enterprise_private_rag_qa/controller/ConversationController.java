package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.service.ConversationService;
import com.yuki.enterprise_private_rag_qa.utils.JwtUtils;
import com.yuki.enterprise_private_rag_qa.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/conversation")
public class ConversationController {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ConversationService conversationService;

    /**
     * 查询对话历史：优先 MySQL 持久化记录，无数据时回退 Redis 当前会话。
     */
    @GetMapping
    public ResponseEntity<?> getConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {

        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_CONVERSATIONS");
        String username = null;
        try {
            username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                monitor.end("获取对话历史失败：无效token");
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }

            LocalDateTime startDateTime = parseDateTime(start_date);
            LocalDateTime endDateTime = parseDateTime(end_date);

            List<Map<String, Object>> messages = conversationService.getConversationMessages(
                    username, startDateTime, endDateTime);

            if (messages.isEmpty()) {
                messages = loadFromRedisFallback(username, start_date, end_date);
            }

            monitor.end("获取对话历史成功");
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取对话历史成功");
            response.put("data", messages);
            return ResponseEntity.ok().body(response);
        } catch (CustomException e) {
            monitor.end("获取对话历史失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            monitor.end("获取对话历史异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }

    private List<Map<String, Object>> loadFromRedisFallback(String username, String startDate, String endDate) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<String> possibleUserIds = List.of(user.getId().toString(), username, String.valueOf(user.getId()));
        for (String uId : possibleUserIds) {
            String conversationId = redisTemplate.opsForValue().get("user:" + uId + ":current_conversation");
            if (conversationId != null) {
                String json = redisTemplate.opsForValue().get("conversation:" + conversationId);
                if (json != null) {
                    return RedisConversationParser.parse(json, startDate, endDate);
                }
            }
        }
        return new ArrayList<>();
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e1) {
            if (dateTimeStr.length() == 10) {
                return LocalDateTime.parse(dateTimeStr + "T00:00:00");
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            return LocalDateTime.parse(dateTimeStr.length() == 16 ? dateTimeStr + ":00" : dateTimeStr, formatter);
        }
    }
}
