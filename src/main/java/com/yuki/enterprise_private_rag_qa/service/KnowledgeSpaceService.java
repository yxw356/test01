package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeSpace;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeSpaceRepository;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeSpaceService {

    private final OrganizationTagRepository organizationTagRepository;
    private final KnowledgeSpaceRepository knowledgeSpaceRepository;

    public KnowledgeSpaceService(OrganizationTagRepository organizationTagRepository) {
        this(organizationTagRepository, null);
    }

    @Autowired
    public KnowledgeSpaceService(OrganizationTagRepository organizationTagRepository, KnowledgeSpaceRepository knowledgeSpaceRepository) {
        this.organizationTagRepository = organizationTagRepository;
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
    }

    public List<KnowledgeSpaceSummary> summarize(List<FileUpload> accessibleDocuments) {
        Map<String, List<FileUpload>> groups = new LinkedHashMap<>();
        groups.put("PUBLIC", new ArrayList<>());

        for (FileUpload document : accessibleDocuments) {
            groups.computeIfAbsent(spaceId(document), ignored -> new ArrayList<>()).add(document);
        }

        return groups.entrySet().stream()
                .filter(entry -> "PUBLIC".equals(entry.getKey()) || !entry.getValue().isEmpty())
                .map(entry -> toSummary(entry.getKey(), entry.getValue()))
                .toList();
    }

    private KnowledgeSpaceSummary toSummary(String id, List<FileUpload> documents) {
        String type = "PUBLIC".equals(id) ? "PUBLIC" : id.startsWith("PRIVATE") ? "PRIVATE" : "DEPARTMENT";
        String departmentId = "DEPARTMENT".equals(type) ? id.replace("DEPARTMENT:", "") : null;
        String title = "PUBLIC".equals(type) ? "公共知识库" : departmentName(departmentId) + "知识库";

        return new KnowledgeSpaceSummary(
                id,
                type,
                title,
                departmentId,
                documents.size(),
                (int) documents.stream().filter(document -> document.getIndexStatus() == FileIndexStatus.INDEXED).count(),
                (int) documents.stream().filter(document -> document.getStatus() != 1).count(),
                (int) documents.stream().filter(document -> document.getStatus() != 1).count(),
                (int) documents.stream().filter(this::hasCleaningIssue).count(),
                documents.stream()
                        .map(document -> document.getMergedAt() != null ? document.getMergedAt() : document.getCreatedAt())
                        .filter(time -> time != null)
                        .max(LocalDateTime::compareTo)
                        .orElse(null)
        );
    }

    private String spaceId(FileUpload document) {
        if (document.getSpaceId() != null && !document.getSpaceId().isBlank()) {
            return document.getSpaceId();
        }
        FileUpload.KnowledgeScope scope = document.getKnowledgeScope();
        if (scope == FileUpload.KnowledgeScope.PUBLIC || document.isPublic()) {
            return "PUBLIC";
        }
        if (scope == FileUpload.KnowledgeScope.PRIVATE) {
            return "PRIVATE";
        }
        String departmentId = document.getDepartmentId() != null && !document.getDepartmentId().isBlank()
                ? document.getDepartmentId()
                : document.getOrgTag();
        return "DEPARTMENT:" + (departmentId == null || departmentId.isBlank() ? "UNKNOWN" : departmentId);
    }

    public String ensureSpaceForDocument(FileUpload document) {
        String spaceId = spaceId(document);
        if (knowledgeSpaceRepository == null || knowledgeSpaceRepository.existsBySpaceId(spaceId)) {
            return spaceId;
        }

        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(spaceId);
        FileUpload.KnowledgeScope scope = document.getKnowledgeScope();
        if (scope == FileUpload.KnowledgeScope.PUBLIC || document.isPublic()) {
            space.setType(KnowledgeSpace.SpaceType.PUBLIC);
            space.setName("公共知识库");
        } else if (scope == FileUpload.KnowledgeScope.PRIVATE) {
            space.setType(KnowledgeSpace.SpaceType.PRIVATE);
            space.setName("个人知识库");
        } else {
            String departmentId = document.getDepartmentId() != null && !document.getDepartmentId().isBlank()
                    ? document.getDepartmentId()
                    : document.getOrgTag();
            space.setType(KnowledgeSpace.SpaceType.DEPARTMENT);
            space.setDepartmentId(departmentId);
            space.setName(departmentName(departmentId) + "知识库");
        }
        knowledgeSpaceRepository.save(space);
        return spaceId;
    }

    private String departmentName(String departmentId) {
        if (departmentId == null || departmentId.isBlank() || "UNKNOWN".equals(departmentId)) {
            return "未归属部门";
        }
        return organizationTagRepository.findByTagId(departmentId)
                .map(tag -> tag.getName() == null || tag.getName().isBlank() ? departmentId : tag.getName())
                .orElse(departmentId);
    }

    private boolean hasCleaningIssue(FileUpload document) {
        return document.getCleaningStatus() == FileUpload.CleaningStatus.FAILED
                || document.getCleaningQualityStatus() == FileUpload.CleaningQualityStatus.WARNING
                || document.getCleaningQualityStatus() == FileUpload.CleaningQualityStatus.FAILED;
    }

    public record KnowledgeSpaceSummary(
            String id,
            String type,
            String title,
            String departmentId,
            int fileCount,
            int indexedCount,
            int processingCount,
            int interruptedCount,
            int cleaningIssueCount,
            LocalDateTime lastUpdatedAt
    ) {
    }
}
