package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.model.KnowledgeCase;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeFaq;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeFaqSuggestion;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeTerm;
import com.yuki.enterprise_private_rag_qa.service.KnowledgeCaseService;
import com.yuki.enterprise_private_rag_qa.service.KnowledgeFaqService;
import com.yuki.enterprise_private_rag_qa.service.KnowledgeFaqSuggestionService;
import com.yuki.enterprise_private_rag_qa.service.KnowledgeTermService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/knowledge-assistant")
public class KnowledgeAssistantConfigController {

    private final KnowledgeFaqService faqService;
    private final KnowledgeTermService termService;
    private final KnowledgeFaqSuggestionService suggestionService;
    private final KnowledgeCaseService caseService;

    public KnowledgeAssistantConfigController(KnowledgeFaqService faqService,
                                              KnowledgeTermService termService,
                                              KnowledgeFaqSuggestionService suggestionService,
                                              KnowledgeCaseService caseService) {
        this.faqService = faqService;
        this.termService = termService;
        this.suggestionService = suggestionService;
        this.caseService = caseService;
    }

    @GetMapping("/cases")
    public ResponseEntity<Map<String, Object>> listCases(@RequestAttribute("userId") String userId) {
        return ok("success", caseService.listManageable(userId).stream().map(this::toCaseDto).toList());
    }

    @PostMapping("/cases")
    public ResponseEntity<Map<String, Object>> createCase(@RequestAttribute("userId") String userId,
                                                          @RequestBody KnowledgeCaseService.CaseRequest request) {
        return ok("案例创建成功", toCaseDto(caseService.create(userId, request)));
    }

    @PutMapping("/cases/{id}")
    public ResponseEntity<Map<String, Object>> updateCase(@RequestAttribute("userId") String userId,
                                                          @PathVariable Long id,
                                                          @RequestBody KnowledgeCaseService.CaseRequest request) {
        return ok("案例已更新", toCaseDto(caseService.update(userId, id, request)));
    }

    @DeleteMapping("/cases/{id}")
    public ResponseEntity<Map<String, Object>> deleteCase(@RequestAttribute("userId") String userId,
                                                          @PathVariable Long id) {
        caseService.delete(userId, id);
        return ok("案例已删除", Map.of("id", id));
    }

    @PostMapping("/cases/policy-draft")
    public ResponseEntity<Map<String, Object>> generateCasePolicyDraft(@RequestAttribute("userId") String userId,
                                                                       @RequestBody KnowledgeCaseService.PolicyDraftRequest request) {
        return ok("制度草案已生成", caseService.generatePolicyDraft(userId, request));
    }

    @GetMapping("/faqs")
    public ResponseEntity<Map<String, Object>> listFaqs(@RequestAttribute("userId") String userId) {
        return ok("success", faqService.listManageable(userId).stream().map(this::toFaqDto).toList());
    }

    @PostMapping("/faqs")
    public ResponseEntity<Map<String, Object>> createFaq(@RequestAttribute("userId") String userId,
                                                         @RequestBody KnowledgeFaqService.FaqRequest request) {
        return ok("问答对创建成功", toFaqDto(faqService.create(userId, request)));
    }

    @PutMapping("/faqs/{id}")
    public ResponseEntity<Map<String, Object>> updateFaq(@RequestAttribute("userId") String userId,
                                                         @PathVariable Long id,
                                                         @RequestBody KnowledgeFaqService.FaqRequest request) {
        return ok("问答对已更新", toFaqDto(faqService.update(userId, id, request)));
    }

    @DeleteMapping("/faqs/{id}")
    public ResponseEntity<Map<String, Object>> deleteFaq(@RequestAttribute("userId") String userId,
                                                         @PathVariable Long id) {
        faqService.delete(userId, id);
        return ok("问答对已删除", Map.of("id", id));
    }

    @GetMapping("/terms")
    public ResponseEntity<Map<String, Object>> listTerms(@RequestAttribute("userId") String userId) {
        return ok("success", termService.listManageable(userId).stream().map(this::toTermDto).toList());
    }

    @PostMapping("/terms")
    public ResponseEntity<Map<String, Object>> createTerm(@RequestAttribute("userId") String userId,
                                                          @RequestBody KnowledgeTermService.TermRequest request) {
        return ok("术语创建成功", toTermDto(termService.create(userId, request)));
    }

    @PutMapping("/terms/{id}")
    public ResponseEntity<Map<String, Object>> updateTerm(@RequestAttribute("userId") String userId,
                                                          @PathVariable Long id,
                                                          @RequestBody KnowledgeTermService.TermRequest request) {
        return ok("术语已更新", toTermDto(termService.update(userId, id, request)));
    }

    @DeleteMapping("/terms/{id}")
    public ResponseEntity<Map<String, Object>> deleteTerm(@RequestAttribute("userId") String userId,
                                                          @PathVariable Long id) {
        termService.delete(userId, id);
        return ok("术语已删除", Map.of("id", id));
    }

    @GetMapping("/faq-suggestions")
    public ResponseEntity<Map<String, Object>> listFaqSuggestions(@RequestAttribute("userId") String userId) {
        return ok("success", suggestionService.listVisible(userId).stream().map(this::toSuggestionDto).toList());
    }

    @PutMapping("/faq-suggestions/{id}/status")
    public ResponseEntity<Map<String, Object>> updateFaqSuggestionStatus(@RequestAttribute("userId") String userId,
                                                                         @PathVariable Long id,
                                                                         @RequestBody SuggestionStatusRequest request) {
        return ok("问答建议状态已更新", toSuggestionDto(suggestionService.updateStatus(userId, id, request.status())));
    }

    private Map<String, Object> toFaqDto(KnowledgeFaq faq) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", faq.getId());
        dto.put("question", faq.getQuestion());
        dto.put("answer", faq.getAnswer());
        dto.put("aliases", faq.getAliases());
        dto.put("knowledgeScope", faq.getKnowledgeScope().name());
        dto.put("departmentId", faq.getDepartmentId());
        dto.put("enabled", faq.isEnabled());
        dto.put("createdAt", faq.getCreatedAt());
        dto.put("updatedAt", faq.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toTermDto(KnowledgeTerm term) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", term.getId());
        dto.put("term", term.getTerm());
        dto.put("definition", term.getDefinition());
        dto.put("synonyms", term.getSynonyms());
        dto.put("knowledgeScope", term.getKnowledgeScope().name());
        dto.put("departmentId", term.getDepartmentId());
        dto.put("enabled", term.isEnabled());
        dto.put("createdAt", term.getCreatedAt());
        dto.put("updatedAt", term.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toCaseDto(KnowledgeCase item) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", item.getId());
        dto.put("title", item.getTitle());
        dto.put("scenario", item.getScenario());
        dto.put("handling", item.getHandling());
        dto.put("conclusion", item.getConclusion());
        dto.put("tags", item.getTags());
        dto.put("knowledgeScope", item.getKnowledgeScope().name());
        dto.put("departmentId", item.getDepartmentId());
        dto.put("status", item.getStatus().name());
        dto.put("enabled", item.isEnabled());
        dto.put("createdAt", item.getCreatedAt());
        dto.put("updatedAt", item.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toSuggestionDto(KnowledgeFaqSuggestion suggestion) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", suggestion.getId());
        dto.put("question", suggestion.getQuestion());
        dto.put("suggestedAnswer", suggestion.getSuggestedAnswer());
        dto.put("knowledgeScope", suggestion.getKnowledgeScope().name());
        dto.put("departmentId", suggestion.getDepartmentId());
        dto.put("evidenceCount", suggestion.getEvidenceCount());
        dto.put("hitCount", suggestion.getHitCount());
        dto.put("status", suggestion.getStatus().name());
        dto.put("lastAskedAt", suggestion.getLastAskedAt());
        dto.put("updatedAt", suggestion.getUpdatedAt());
        return dto;
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    public record SuggestionStatusRequest(KnowledgeFaqSuggestion.SuggestionStatus status) {
    }
}
