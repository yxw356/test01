package com.yuki.enterprise_private_rag_qa.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.model.UserKnowledgeSpaceLayout;
import com.yuki.enterprise_private_rag_qa.repository.UserKnowledgeSpaceLayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeSpaceLayoutService {

    private final UserKnowledgeSpaceLayoutRepository layoutRepository;
    private final ObjectMapper objectMapper;

    public KnowledgeSpaceLayoutService(UserKnowledgeSpaceLayoutRepository layoutRepository,
                                       ObjectMapper objectMapper) {
        this.layoutRepository = layoutRepository;
        this.objectMapper = objectMapper;
    }

    public LayoutDto getLayout(String userId) {
        return layoutRepository.findByUserId(userId)
                .map(this::toDto)
                .orElse(new LayoutDto(List.of(), List.of()));
    }

    @Transactional
    public LayoutDto saveLayout(String userId, List<String> spaceOrder, List<String> collapsedSpaces) {
        UserKnowledgeSpaceLayout layout = layoutRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserKnowledgeSpaceLayout created = new UserKnowledgeSpaceLayout();
                    created.setUserId(userId);
                    return created;
                });
        layout.setSpaceOrder(writeJson(spaceOrder == null ? List.of() : spaceOrder));
        layout.setCollapsedSpaces(writeJson(collapsedSpaces == null ? List.of() : collapsedSpaces));
        layoutRepository.save(layout);
        return toDto(layout);
    }

    private LayoutDto toDto(UserKnowledgeSpaceLayout layout) {
        return new LayoutDto(readJson(layout.getSpaceOrder()), readJson(layout.getCollapsedSpaces()));
    }

    private List<String> readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? new ArrayList<>() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    public record LayoutDto(List<String> spaceOrder, List<String> collapsedSpaces) {
    }
}
