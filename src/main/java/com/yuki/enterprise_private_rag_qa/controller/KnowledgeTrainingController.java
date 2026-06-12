package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.service.TrainingDeckService;
import com.yuki.enterprise_private_rag_qa.service.TrainingDeckExportService;
import com.yuki.enterprise_private_rag_qa.service.TrainingExamService;
import com.yuki.enterprise_private_rag_qa.service.TrainingQuizService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge-training")
public class KnowledgeTrainingController {

    private final TrainingQuizService trainingQuizService;
    private final TrainingDeckService trainingDeckService;
    private final TrainingDeckExportService trainingDeckExportService;
    private final TrainingExamService trainingExamService;

    public KnowledgeTrainingController(TrainingQuizService trainingQuizService,
                                       TrainingDeckService trainingDeckService,
                                       TrainingDeckExportService trainingDeckExportService,
                                       TrainingExamService trainingExamService) {
        this.trainingQuizService = trainingQuizService;
        this.trainingDeckService = trainingDeckService;
        this.trainingDeckExportService = trainingDeckExportService;
        this.trainingExamService = trainingExamService;
    }

    @PostMapping("/quiz")
    public ResponseEntity<Map<String, Object>> generateQuiz(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("orgTags") String orgTags,
            @RequestBody TrainingQuizService.QuizGenerationRequest request) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "题库生成成功");
            response.put("data", trainingQuizService.generate(userId, orgTags, request));
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            return error(e.getStatus(), e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "题库生成失败: " + e.getMessage());
        }
    }

    @PostMapping("/quiz/submit")
    public ResponseEntity<Map<String, Object>> submitQuiz(
            @RequestAttribute("userId") String userId,
            @RequestBody TrainingExamService.SubmitRequest request) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "试题已自动审阅");
            response.put("data", trainingExamService.submit(userId, request));
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            return error(e.getStatus(), e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "试题审阅失败: " + e.getMessage());
        }
    }

    @GetMapping("/quiz/ranking")
    public ResponseEntity<Map<String, Object>> quizRanking(
            @RequestAttribute("userId") String userId,
            @RequestParam(defaultValue = "PUBLIC") String knowledgeScope,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Integer limit) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "部门排名获取成功");
            response.put("data", trainingExamService.ranking(userId, knowledgeScope, departmentId, limit));
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            return error(e.getStatus(), e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "部门排名获取失败: " + e.getMessage());
        }
    }

    @PostMapping("/deck")
    public ResponseEntity<Map<String, Object>> generateDeck(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("orgTags") String orgTags,
            @RequestBody TrainingDeckService.DeckGenerationRequest request) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "培训课件生成成功");
            response.put("data", trainingDeckService.generate(userId, orgTags, request));
            return ResponseEntity.ok(response);
        } catch (CustomException e) {
            return error(e.getStatus(), e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "培训课件生成失败: " + e.getMessage());
        }
    }

    @PostMapping("/deck/export")
    public ResponseEntity<byte[]> exportDeck(@RequestBody TrainingDeckService.DeckGenerationResult deck) {
        byte[] content = trainingDeckExportService.export(deck);
        String fileName = safeFileName(deck.title()) + ".pptx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .contentLength(content.length)
                .body(content);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", status.value());
        response.put("message", message);
        response.put("data", null);
        return ResponseEntity.status(status).body(response);
    }

    private String safeFileName(String title) {
        String value = title == null || title.isBlank() ? "培训课件" : title;
        return value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
    }
}
