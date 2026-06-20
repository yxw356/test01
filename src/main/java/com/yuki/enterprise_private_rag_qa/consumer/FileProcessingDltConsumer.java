package com.yuki.enterprise_private_rag_qa.consumer;

import com.yuki.enterprise_private_rag_qa.model.FileProcessingTask;
import com.yuki.enterprise_private_rag_qa.service.IndexFailureAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FileProcessingDltConsumer {

    private final IndexFailureAlertService indexFailureAlertService;

    public FileProcessingDltConsumer(IndexFailureAlertService indexFailureAlertService) {
        this.indexFailureAlertService = indexFailureAlertService;
    }

    @KafkaListener(topics = "${spring.kafka.topic.dlt}", groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void handleDeadLetter(FileProcessingTask task) {
        log.warn("Received file processing DLT message: fileMd5={}, fileName={}", task.getFileMd5(), task.getFileName());
        indexFailureAlertService.recordDeadLetter(task);
    }
}
