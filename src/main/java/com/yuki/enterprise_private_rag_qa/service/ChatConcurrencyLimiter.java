package com.yuki.enterprise_private_rag_qa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ChatConcurrencyLimiter {

    private final int maxActive;
    private final long waitTimeoutMs;
    private final Semaphore semaphore;
    private final AtomicLong rejectedCount = new AtomicLong();

    public ChatConcurrencyLimiter(
            @Value("${chat.concurrency.max-active:8}") int maxActive,
            @Value("${chat.concurrency.wait-timeout-ms:3000}") long waitTimeoutMs) {
        this.maxActive = Math.max(0, maxActive);
        this.waitTimeoutMs = Math.max(0, waitTimeoutMs);
        this.semaphore = this.maxActive > 0 ? new Semaphore(this.maxActive, true) : null;
    }

    public boolean tryAcquire() {
        if (semaphore == null) {
            return true;
        }
        try {
            boolean acquired = semaphore.tryAcquire(waitTimeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                rejectedCount.incrementAndGet();
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rejectedCount.incrementAndGet();
            return false;
        }
    }

    public void release() {
        if (semaphore != null && semaphore.availablePermits() < maxActive) {
            semaphore.release();
        }
    }

    public int getActiveCount() {
        if (semaphore == null) {
            return 0;
        }
        return maxActive - semaphore.availablePermits();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }
}
