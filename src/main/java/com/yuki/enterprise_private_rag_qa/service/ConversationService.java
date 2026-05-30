package com.yuki.enterprise_private_rag_qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.entity.SearchResult;
import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.Conversation;
import com.yuki.enterprise_private_rag_qa.model.RetrievalCitation;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.ConversationRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationRepository conversationRepository,
                               UserRepository userRepository,
                               ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordConversation(String username,
                                     String question,
                                     String answer,
                                     String sessionId,
                                     List<SearchResult> retrievalResults) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Conversation conversation = new Conversation();
        conversation.setUser(user);
        conversation.setQuestion(question);
        conversation.setAnswer(answer);
        conversation.setSessionId(sessionId);
        conversation.setRetrievalCitations(serializeCitations(retrievalResults));
        conversationRepository.save(conversation);
        logger.info("对话已落库: user={}, sessionId={}, citations={}",
                username, sessionId, retrievalResults == null ? 0 : retrievalResults.size());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversationMessages(String username,
                                                              LocalDateTime startDate,
                                                              LocalDateTime endDate) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        List<Conversation> records = queryByUser(user.getId(), startDate, endDate);
        return toMessageList(records, null);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAdminConversationMessages(Long targetUserId,
                                                                   LocalDateTime startDate,
                                                                   LocalDateTime endDate) {
        List<Conversation> records;
        if (targetUserId != null) {
            records = queryByUser(targetUserId, startDate, endDate);
            User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new CustomException("Target user not found", HttpStatus.NOT_FOUND));
            return toMessageList(records, targetUser.getUsername());
        }
        records = queryAll(startDate, endDate);
        return toAdminMessageList(records);
    }

    public static List<RetrievalCitation> buildCitations(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<RetrievalCitation> citations = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            citations.add(RetrievalCitation.fromSearchResult(i + 1, results.get(i)));
        }
        return citations;
    }

    private List<Conversation> queryByUser(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Conversation> records;
        if (startDate != null && endDate != null) {
            records = conversationRepository.findByUserIdAndTimestampBetween(userId, startDate, endDate);
        } else {
            records = conversationRepository.findByUserId(userId);
        }
        records.sort(Comparator.comparing(Conversation::getTimestamp));
        return records;
    }

    private List<Conversation> queryAll(LocalDateTime startDate, LocalDateTime endDate) {
        List<Conversation> records;
        if (startDate != null && endDate != null) {
            records = conversationRepository.findByTimestampBetween(startDate, endDate);
        } else {
            records = conversationRepository.findAll();
        }
        records.sort(Comparator.comparing(Conversation::getTimestamp));
        return records;
    }

    private List<Map<String, Object>> toMessageList(List<Conversation> records, String username) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Conversation record : records) {
            String timestamp = formatTimestamp(record.getTimestamp());
            messages.add(buildMessage("user", record.getQuestion(), timestamp, null, username));
            messages.add(buildMessage("assistant", record.getAnswer(), timestamp,
                    parseCitations(record.getRetrievalCitations()), username));
        }
        return messages;
    }

    private List<Map<String, Object>> toAdminMessageList(List<Conversation> records) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Conversation record : records) {
            String username = record.getUser() != null ? record.getUser().getUsername() : "unknown";
            String timestamp = formatTimestamp(record.getTimestamp());
            messages.add(buildMessage("user", record.getQuestion(), timestamp, null, username));
            messages.add(buildMessage("assistant", record.getAnswer(), timestamp,
                    parseCitations(record.getRetrievalCitations()), username));
        }
        return messages;
    }

    private Map<String, Object> buildMessage(String role,
                                             String content,
                                             String timestamp,
                                             List<RetrievalCitation> citations,
                                             String username) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", timestamp);
        if (username != null) {
            message.put("username", username);
        }
        if (citations != null && !citations.isEmpty()) {
            message.put("citations", citations);
        }
        return message;
    }

    private String serializeCitations(List<SearchResult> retrievalResults) {
        List<RetrievalCitation> citations = buildCitations(retrievalResults);
        if (citations.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            logger.warn("序列化检索引用失败: {}", e.getMessage());
            return null;
        }
    }

    private List<RetrievalCitation> parseCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RetrievalCitation>>() {});
        } catch (JsonProcessingException e) {
            logger.warn("解析检索引用失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        return timestamp == null ? "未知时间" : timestamp.format(TIMESTAMP_FORMAT);
    }
}
