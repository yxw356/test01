package com.yuki.enterprise_private_rag_qa.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditSupportTest {

    @Test
    void parseClientInfoRecognizesDesktopChromeOnMac() {
        String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

        AuditSupport.ClientInfo info = AuditSupport.clientInfo(userAgent);

        assertEquals("Desktop", info.deviceType());
        assertEquals("Chrome", info.browser());
        assertEquals("macOS", info.os());
        assertEquals(userAgent, info.userAgent());
    }

    @Test
    void parseClientInfoRecognizesMobileSafariOnIos() {
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

        AuditSupport.ClientInfo info = AuditSupport.clientInfo(userAgent);

        assertEquals("Mobile", info.deviceType());
        assertEquals("Safari", info.browser());
        assertEquals("iOS", info.os());
    }
}
