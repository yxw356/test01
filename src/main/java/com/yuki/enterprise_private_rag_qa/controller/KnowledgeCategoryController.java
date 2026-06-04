package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeCategory;
import com.yuki.enterprise_private_rag_qa.service.KnowledgeCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge-categories")
public class KnowledgeCategoryController {

    private final KnowledgeCategoryService categoryService;

    public KnowledgeCategoryController(KnowledgeCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestAttribute("userId") String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "success");
        response.put("data", categoryService.listVisibleCategories(userId).stream().map(this::toDto).toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestAttribute("userId") String userId,
                                                      @RequestBody KnowledgeCategoryService.CategoryCreateRequest request) {
        KnowledgeCategory category = categoryService.createCategory(userId, request);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "知识分类创建成功");
        response.put("data", toDto(category));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toDto(KnowledgeCategory category) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", category.getId());
        dto.put("name", category.getName());
        dto.put("parentId", category.getParentId());
        dto.put("knowledgeScope", category.getKnowledgeScope().name());
        dto.put("departmentId", category.getDepartmentId());
        dto.put("description", category.getDescription());
        dto.put("sortOrder", category.getSortOrder());
        dto.put("enabled", category.isEnabled());
        return dto;
    }
}
