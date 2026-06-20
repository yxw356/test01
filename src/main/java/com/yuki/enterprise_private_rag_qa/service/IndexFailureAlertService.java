package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileProcessingTask;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class IndexFailureAlertService {

    private static final Logger logger = LoggerFactory.getLogger(IndexFailureAlertService.class);

    private final FileUploadRepository fileUploadRepository;
    private final List<Map<String, Object>> recentDeadLetters = new CopyOnWriteArrayList<>();
    private final WebClient webhookClient;
    private final String webhookUrl;

    public IndexFailureAlertService(FileUploadRepository fileUploadRepository,
                                    @Value("${ops.index-failure.webhook-url:}") String webhookUrl) {
        this.fileUploadRepository = fileUploadRepository;
        this.webhookUrl = webhookUrl;
        this.webhookClient = (webhookUrl == null || webhookUrl.isBlank())
                ? null
                : WebClient.builder().baseUrl(webhookUrl).build();
    }

    public void recordDeadLetter(FileProcessingTask task) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("fileMd5", task.getFileMd5());
        record.put("fileName", task.getFileName());
        record.put("userId", task.getUserId());
        record.put("recordedAt", LocalDateTime.now().toString());
        recentDeadLetters.add(0, record);
        while (recentDeadLetters.size() > 100) {
            recentDeadLetters.remove(recentDeadLetters.size() - 1);
        }
        notifyWebhook(record);
    }

    public List<Map<String, Object>> listIndexFailures() {
        List<Map<String, Object>> failures = new ArrayList<>(recentDeadLetters);
        fileUploadRepository.findAll().stream()
                .filter(file -> file.getIndexStatus() == FileIndexStatus.FAILED)
                .map(this::toFailureRecord)
                .forEach(record -> {
                    if (failures.stream().noneMatch(item -> record.get("fileMd5").equals(item.get("fileMd5")))) {
                        failures.add(record);
                    }
                });
        return failures;
    }

    private Map<String, Object> toFailureRecord(FileUpload file) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("fileMd5", file.getFileMd5());
        record.put("fileName", file.getFileName());
        record.put("userId", file.getUserId());
        record.put("indexError", file.getIndexError());
        record.put("recordedAt", file.getMergedAt() != null ? file.getMergedAt().toString() : file.getCreatedAt().toString());
        record.put("source", "database");
        return record;
    }

    private void notifyWebhook(Map<String, Object> record) {
        if (webhookClient == null) {
            return;
        }
        try {
            webhookClient.post()
                    .bodyValue(Map.of(
                            "msgtype", "text",
                            "text", Map.of("content", "索引失败告警: " + record.get("fileName") + " (" + record.get("fileMd5") + ")")
                    ))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (Exception e) {
            logger.warn("Failed to send index failure webhook: {}", e.getMessage());
        }
    }
}
