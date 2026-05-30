package com.yuki.enterprise_private_rag_qa.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.model.RetrievalCitation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Redis 会话 JSON 转为前端消息列表（兼容旧数据）。
 */
final class RedisConversationParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private RedisConversationParser() {
    }

    static List<Map<String, Object>> parse(String json, String startDate, String endDate) {
        try {
            List<Map<String, String>> history = MAPPER.readValue(json, new TypeReference<>() {});
            LocalDateTime start = parseBoundary(startDate, true);
            LocalDateTime end = parseBoundary(endDate, false);

            List<Map<String, Object>> messages = new ArrayList<>();
            for (Map<String, String> message : history) {
                String timestamp = message.getOrDefault("timestamp", "未知时间");
                if (!withinRange(timestamp, start, end)) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("role", message.get("role"));
                item.put("content", message.get("content"));
                item.put("timestamp", timestamp);
                String citationsJson = message.get("citations");
                if (citationsJson != null && !citationsJson.isBlank()) {
                    item.put("citations", MAPPER.readValue(citationsJson, new TypeReference<List<RetrievalCitation>>() {}));
                }
                messages.add(item);
            }
            return messages;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static boolean withinRange(String timestamp, LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) {
            return true;
        }
        if ("未知时间".equals(timestamp)) {
            return start == null && end == null;
        }
        try {
            LocalDateTime messageTime = LocalDateTime.parse(timestamp, FORMAT);
            if (start != null && messageTime.isBefore(start)) {
                return false;
            }
            return end == null || !messageTime.isAfter(end);
        } catch (Exception e) {
            return true;
        }
    }

    private static LocalDateTime parseBoundary(String value, boolean startOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() == 10) {
            return LocalDateTime.parse(value + (startOfDay ? "T00:00:00" : "T23:59:59"));
        }
        return LocalDateTime.parse(value.length() == 16 ? value + ":00" : value);
    }
}
