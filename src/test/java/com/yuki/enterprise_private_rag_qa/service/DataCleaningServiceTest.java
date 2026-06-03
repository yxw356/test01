package com.yuki.enterprise_private_rag_qa.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCleaningServiceTest {

    private final DataCleaningService service = new DataCleaningService();

    @Test
    void cleanNormalizesWhitespaceAndRemovesDuplicateLines() {
        String raw = """
                第一章  总则


                第一条   公司制度说明。
                第一条   公司制度说明。

                第二条\t员工应遵守流程。
                """;

        DataCleaningService.CleaningResult result = service.clean(raw);

        assertEquals("""
                第一章 总则

                第一条 公司制度说明。

                第二条 员工应遵守流程。""", result.cleanedText());
        assertEquals(raw.length(), result.originalChars());
        assertEquals(result.cleanedText().length(), result.cleanedChars());
        assertTrue(result.removedChars() > 0);
        assertEquals(1, result.duplicateLinesRemoved());
        assertTrue(result.compressionRatio() > 0);
    }

    @Test
    void cleanKeepsMeaningfulRepeatedShortLabels() {
        String raw = """
                是
                是
                否
                """;

        DataCleaningService.CleaningResult result = service.clean(raw);

        assertEquals("是\n是\n否", result.cleanedText());
        assertEquals(0, result.duplicateLinesRemoved());
    }

    @Test
    void cleanCanDisableDuplicateLineRemovalPerRuleConfig() {
        String raw = """
                第一条 公司制度说明。
                第一条 公司制度说明。
                """;

        DataCleaningService.CleaningRuleConfig config = DataCleaningService.CleaningRuleConfig.defaultConfig()
                .withRemoveDuplicateLines(false);

        DataCleaningService.CleaningResult result = service.clean(raw, config);

        assertEquals("第一条 公司制度说明。\n第一条 公司制度说明。", result.cleanedText());
        assertEquals(0, result.duplicateLinesRemoved());
    }

    @Test
    void cleanCanDropLinesMatchingConfiguredPatterns() {
        String raw = """
                企业制度正文
                第 1 页 / 共 3 页
                内部资料
                """;

        DataCleaningService.CleaningRuleConfig config = DataCleaningService.CleaningRuleConfig.defaultConfig()
                .withDropLinePatterns(List.of("^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$"));

        DataCleaningService.CleaningResult result = service.clean(raw, config);

        assertEquals("企业制度正文\n内部资料", result.cleanedText());
    }
}
