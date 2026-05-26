package com.yuki.enterprise_private_rag_qa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TabularParseServiceTest {

    private TabularParseService tabularParseService;

    @BeforeEach
    void setUp() {
        tabularParseService = new TabularParseService();
    }

    @Test
    void detectsTabularExtensions() {
        assertTrue(tabularParseService.isTabular("report.xlsx"));
        assertTrue(tabularParseService.isTabular("data.csv"));
        assertFalse(tabularParseService.isTabular("notes.txt"));
    }

    @Test
    void parseCsvPreservesColumnValues() throws Exception {
        String csv = "region,quarter,amount\n华东,Q3,1200\n华北,Q3,900\n";
        String text = tabularParseService.extractText(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), "sales.csv");

        assertTrue(text.contains("[TABLE"));
        assertTrue(text.contains("region=华东"));
        assertTrue(text.contains("quarter=Q3"));
        assertTrue(text.contains("amount=1200"));
        assertTrue(text.contains("region=华北"));
        assertTrue(text.contains("amount=900"));
    }
}
