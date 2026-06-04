package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.model.CleaningRuleSet;
import com.yuki.enterprise_private_rag_qa.service.CleaningRuleSetService;
import com.yuki.enterprise_private_rag_qa.service.DataCleaningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-cleaning")
public class DataCleaningController {

    private final DataCleaningService dataCleaningService;
    private final CleaningRuleSetService ruleSetService;

    public DataCleaningController(DataCleaningService dataCleaningService,
                                  CleaningRuleSetService ruleSetService) {
        this.dataCleaningService = dataCleaningService;
        this.ruleSetService = ruleSetService;
    }

    @GetMapping("/rules/default")
    public ResponseEntity<Map<String, Object>> defaultRules() {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "获取默认清洗规则成功");
        response.put("data", toRuleMap(dataCleaningService.currentDefaultConfig()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestAttribute(value = "userId", required = false) String userId,
                                                       @RequestBody PreviewRequest request) {
        DataCleaningService.CleaningRuleConfig config = request.ruleConfig();
        if (request.ruleSetId() != null && ruleSetService != null) {
            config = ruleSetService.resolveRuleConfig(request.ruleSetId(), userId);
        }
        DataCleaningService.CleaningResult result = dataCleaningService.clean(
                request.rawText(),
                config
        );
        DataCleaningService.CleaningQualityReport qualityReport = dataCleaningService.assessQuality(result);

        Map<String, Object> data = new HashMap<>();
        data.put("cleanedText", result.cleanedText());
        data.put("originalChars", result.originalChars());
        data.put("cleanedChars", result.cleanedChars());
        data.put("removedChars", result.removedChars());
        data.put("duplicateLinesRemoved", result.duplicateLinesRemoved());
        data.put("compressionRatio", result.compressionRatio());
        data.put("qualityStatus", qualityReport.status().name());
        data.put("qualityIssues", qualityReport.issues());
        data.put("qualityScore", qualityReport.score());

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "清洗预览成功");
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<Map<String, Object>> preview(PreviewRequest request) {
        return preview(null, request);
    }

    @GetMapping("/rule-sets")
    public ResponseEntity<Map<String, Object>> listRuleSets(@RequestAttribute("userId") String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "获取清洗规则集成功");
        response.put("data", ruleSetService.listVisibleRuleSets(userId).stream().map(this::toRuleSetDto).toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rule-sets")
    public ResponseEntity<Map<String, Object>> createRuleSet(
            @RequestAttribute("userId") String userId,
            @RequestBody CleaningRuleSetService.RuleSetRequest request) {
        CleaningRuleSet ruleSet = ruleSetService.createRuleSet(userId, request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "清洗规则集创建成功");
        response.put("data", toRuleSetDto(ruleSet));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/rule-sets/{ruleSetId}")
    public ResponseEntity<Map<String, Object>> updateRuleSet(
            @RequestAttribute("userId") String userId,
            @PathVariable Long ruleSetId,
            @RequestBody CleaningRuleSetService.RuleSetRequest request) {
        CleaningRuleSet ruleSet = ruleSetService.updateRuleSet(userId, ruleSetId, request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "清洗规则集更新成功");
        response.put("data", toRuleSetDto(ruleSet));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/rule-sets/{ruleSetId}")
    public ResponseEntity<Map<String, Object>> disableRuleSet(
            @RequestAttribute("userId") String userId,
            @PathVariable Long ruleSetId) {
        ruleSetService.disableRuleSet(userId, ruleSetId);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "清洗规则集已停用");
        return ResponseEntity.ok(response);
    }

    public record PreviewRequest(
            String rawText,
            DataCleaningService.CleaningRuleConfig ruleConfig,
            Long ruleSetId
    ) {
    }

    private Map<String, Object> toRuleSetDto(CleaningRuleSet ruleSet) {
        Map<String, Object> data = new HashMap<>();
        data.put("normalizeLineBreaks", ruleSet.isNormalizeLineBreaks());
        data.put("normalizeUnicodeSpaces", ruleSet.isNormalizeUnicodeSpaces());
        data.put("normalizeWhitespace", ruleSet.isNormalizeWhitespace());
        data.put("trimLines", ruleSet.isTrimLines());
        data.put("collapseBlankLines", ruleSet.isCollapseBlankLines());
        data.put("removeDuplicateLines", ruleSet.isRemoveDuplicateLines());
        data.put("minDuplicateLineLength", ruleSet.getMinDuplicateLineLength());
        data.put("dropLinePatterns", parsePatterns(ruleSet.getDropLinePatterns()));
        data.put("id", ruleSet.getId());
        data.put("name", ruleSet.getName());
        data.put("knowledgeScope", ruleSet.getKnowledgeScope().name());
        data.put("departmentId", ruleSet.getDepartmentId());
        data.put("description", ruleSet.getDescription());
        data.put("enabled", ruleSet.isEnabled());
        data.put("createdBy", ruleSet.getCreatedBy());
        data.put("createdAt", ruleSet.getCreatedAt());
        data.put("updatedAt", ruleSet.getUpdatedAt());
        return data;
    }

    private java.util.List<String> parsePatterns(String value) {
        if (value == null || value.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(value.split("\\s*\\Q;;\\E\\s*")).stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .toList();
    }

    private Map<String, Object> toRuleMap(DataCleaningService.CleaningRuleConfig config) {
        Map<String, Object> data = new HashMap<>();
        data.put("normalizeLineBreaks", config.normalizeLineBreaks());
        data.put("normalizeUnicodeSpaces", config.normalizeUnicodeSpaces());
        data.put("normalizeWhitespace", config.normalizeWhitespace());
        data.put("trimLines", config.trimLines());
        data.put("collapseBlankLines", config.collapseBlankLines());
        data.put("removeDuplicateLines", config.removeDuplicateLines());
        data.put("minDuplicateLineLength", config.minDuplicateLineLength());
        data.put("dropLinePatterns", config.dropLinePatterns());
        return data;
    }
}
