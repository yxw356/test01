package com.yuki.enterprise_private_rag_qa.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatConcurrencyLimiterTest {

    @Test
    void rejectsWhenAllChatSlotsAreBusyAndReleasesSlots() {
        ChatConcurrencyLimiter limiter = new ChatConcurrencyLimiter(1, 1);

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        assertEquals(1, limiter.getActiveCount());
        assertEquals(1, limiter.getRejectedCount());

        limiter.release();

        assertEquals(0, limiter.getActiveCount());
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void disabledLimiterDoesNotBlockRequests() {
        ChatConcurrencyLimiter limiter = new ChatConcurrencyLimiter(0, 1);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertEquals(0, limiter.getActiveCount());

        limiter.release();
        assertEquals(0, limiter.getActiveCount());
    }
}
