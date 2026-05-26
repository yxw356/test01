package com.yuki.enterprise_private_rag_qa.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行时业务指标（内存计数，供管理端与监控探针读取）
 */
@Component
public class OperationMetricsService {

    private final AtomicLong indexSuccessCount = new AtomicLong();
    private final AtomicLong indexFailureCount = new AtomicLong();
    private final AtomicLong chatRequestCount = new AtomicLong();
    private final AtomicLong chatTotalDurationMs = new AtomicLong();

    private volatile String lastIndexFailureMessage;
    private volatile Instant lastIndexFailureAt;
    private volatile long chatP95EstimateMs;

    public void recordIndexSuccess() {
        indexSuccessCount.incrementAndGet();
    }

    public void recordIndexFailure(String message) {
        indexFailureCount.incrementAndGet();
        lastIndexFailureMessage = message;
        lastIndexFailureAt = Instant.now();
    }

    public void recordChatDuration(long durationMs) {
        chatRequestCount.incrementAndGet();
        chatTotalDurationMs.addAndGet(durationMs);
        chatP95EstimateMs = Math.max(chatP95EstimateMs, (long) (chatP95EstimateMs * 0.9 + durationMs * 0.1));
    }

    public long getIndexSuccessCount() {
        return indexSuccessCount.get();
    }

    public long getIndexFailureCount() {
        return indexFailureCount.get();
    }

    public String getLastIndexFailureMessage() {
        return lastIndexFailureMessage;
    }

    public Instant getLastIndexFailureAt() {
        return lastIndexFailureAt;
    }

    public long getChatRequestCount() {
        return chatRequestCount.get();
    }

    public long getChatAverageDurationMs() {
        long count = chatRequestCount.get();
        return count == 0 ? 0 : chatTotalDurationMs.get() / count;
    }

    public long getChatP95EstimateMs() {
        return chatP95EstimateMs;
    }
}
