package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.model.CleaningRuleSet;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.service.CleaningRuleSetService;
import com.yuki.enterprise_private_rag_qa.service.DataCleaningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataCleaningControllerTest {

    private DataCleaningController controller;
    private CleaningRuleSetService ruleSetService;

    @BeforeEach
    void setUp() {
        ruleSetService = mock(CleaningRuleSetService.class);
        controller = new DataCleaningController(new DataCleaningService(), ruleSetService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewReturnsCleanedTextAndStatsWithCustomRules() {
        DataCleaningController.PreviewRequest request = new DataCleaningController.PreviewRequest(
                """
                        企业制度正文
                        第 1 页 / 共 3 页
                        第一条 公司制度说明。
                        第一条 公司制度说明。
                        """,
                DataCleaningService.CleaningRuleConfig.defaultConfig()
                        .withDropLinePatterns(List.of("^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$")),
                null
        );

        ResponseEntity<Map<String, Object>> response = controller.preview(request);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertEquals(200, body.get("code"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals("企业制度正文\n第一条 公司制度说明。", data.get("cleanedText"));
        assertEquals(1, data.get("duplicateLinesRemoved"));
        assertTrue((Integer) data.get("removedChars") > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultRulesExposeCurrentConfiguration() {
        ResponseEntity<Map<String, Object>> response = controller.defaultRules();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(true, data.get("normalizeWhitespace"));
        assertEquals(true, data.get("removeDuplicateLines"));
        assertEquals(8, data.get("minDuplicateLineLength"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRuleSetsReturnsVisibleRuleSets() {
        CleaningRuleSet ruleSet = ruleSet(3L, "公共规则", FileUpload.KnowledgeScope.PUBLIC, null);
        when(ruleSetService.listVisibleRuleSets("1")).thenReturn(List.of(ruleSet));

        ResponseEntity<Map<String, Object>> response = controller.listRuleSets("1");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertEquals(1, data.size());
        assertEquals(3L, data.get(0).get("id"));
        assertEquals("公共规则", data.get(0).get("name"));
        assertEquals("PUBLIC", data.get(0).get("knowledgeScope"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRuleSetDelegatesToService() {
        CleaningRuleSetService.RuleSetRequest request = new CleaningRuleSetService.RuleSetRequest(
                "公共规则", FileUpload.KnowledgeScope.PUBLIC, null, "",
                true, true, true, true, true, true, 8, List.of("^内部资料$")
        );
        CleaningRuleSet created = ruleSet(9L, "公共规则", FileUpload.KnowledgeScope.PUBLIC, null);
        when(ruleSetService.createRuleSet("1", request)).thenReturn(created);

        ResponseEntity<Map<String, Object>> response = controller.createRuleSet("1", request);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(9L, data.get("id"));
        verify(ruleSetService).createRuleSet("1", request);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateRuleSetDelegatesToService() {
        CleaningRuleSetService.RuleSetRequest request = new CleaningRuleSetService.RuleSetRequest(
                "更新规则", FileUpload.KnowledgeScope.PUBLIC, null, "更新说明",
                true, true, false, true, true, false, 12, List.of("^页脚$")
        );
        CleaningRuleSet updated = ruleSet(10L, "更新规则", FileUpload.KnowledgeScope.PUBLIC, null);
        updated.setDescription("更新说明");
        updated.setNormalizeWhitespace(false);
        updated.setRemoveDuplicateLines(false);
        updated.setMinDuplicateLineLength(12);
        updated.setDropLinePatterns("^页脚$");
        when(ruleSetService.updateRuleSet("1", 10L, request)).thenReturn(updated);

        ResponseEntity<Map<String, Object>> response = controller.updateRuleSet("1", 10L, request);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals(10L, data.get("id"));
        assertEquals("更新规则", data.get("name"));
        assertEquals(false, data.get("normalizeWhitespace"));
        assertEquals(false, data.get("removeDuplicateLines"));
        assertEquals(List.of("^页脚$"), data.get("dropLinePatterns"));
        verify(ruleSetService).updateRuleSet("1", 10L, request);
    }

    @Test
    void disableRuleSetDelegatesToService() {
        ResponseEntity<Map<String, Object>> response = controller.disableRuleSet("1", 11L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("清洗规则集已停用", response.getBody().get("message"));
        verify(ruleSetService).disableRuleSet("1", 11L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewCanUsePersistedRuleSet() {
        when(ruleSetService.resolveRuleConfig(8L, "1")).thenReturn(
                DataCleaningService.CleaningRuleConfig.defaultConfig()
                        .withDropLinePatterns(List.of("^内部资料$"))
        );
        DataCleaningController.PreviewRequest request = new DataCleaningController.PreviewRequest(
                "正文\n内部资料\n正文", null, 8L
        );

        ResponseEntity<Map<String, Object>> response = controller.preview("1", request);

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("正文\n正文", data.get("cleanedText"));
        verify(ruleSetService).resolveRuleConfig(8L, "1");
    }

    private CleaningRuleSet ruleSet(Long id, String name, FileUpload.KnowledgeScope scope, String departmentId) {
        CleaningRuleSet ruleSet = new CleaningRuleSet();
        ruleSet.setId(id);
        ruleSet.setName(name);
        ruleSet.setKnowledgeScope(scope);
        ruleSet.setDepartmentId(departmentId);
        ruleSet.setEnabled(true);
        ruleSet.setNormalizeLineBreaks(true);
        ruleSet.setNormalizeUnicodeSpaces(true);
        ruleSet.setNormalizeWhitespace(true);
        ruleSet.setTrimLines(true);
        ruleSet.setCollapseBlankLines(true);
        ruleSet.setRemoveDuplicateLines(true);
        ruleSet.setMinDuplicateLineLength(8);
        return ruleSet;
    }
}
