package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeSpace;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeSpaceRepository;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeSpaceService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeSpaceService.class);
    private static final String PRIVATE_SPACE_ID = "PRIVATE";

    private final OrganizationTagRepository organizationTagRepository;
    private final KnowledgeSpaceRepository knowledgeSpaceRepository;
    private final FileUploadRepository fileUploadRepository;

    public KnowledgeSpaceService(OrganizationTagRepository organizationTagRepository) {
        this(organizationTagRepository, null, null);
    }

    @Autowired
    public KnowledgeSpaceService(OrganizationTagRepository organizationTagRepository,
                                 KnowledgeSpaceRepository knowledgeSpaceRepository,
                                 FileUploadRepository fileUploadRepository) {
        this.organizationTagRepository = organizationTagRepository;
        this.knowledgeSpaceRepository = knowledgeSpaceRepository;
        this.fileUploadRepository = fileUploadRepository;
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
        String title = switch (type) {
            case "PUBLIC" -> "公共知识库";
            case "PRIVATE" -> "个人知识库";
            default -> departmentName(departmentId) + "知识库";
        };

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
        ensureSpaceRecord(spaceId, document);
        return spaceId;
    }

    @Transactional
    public BackfillResult backfillSpacesAtStartup() {
        if (knowledgeSpaceRepository == null) {
            return new BackfillResult(0, 0);
        }

        int createdSpaces = 0;
        createdSpaces += ensureSpaceRecord("PUBLIC", null) ? 1 : 0;
        createdSpaces += ensureSpaceRecord(PRIVATE_SPACE_ID, null) ? 1 : 0;

        for (var tag : organizationTagRepository.findAll()) {
            if (tag.getTagId() == null || tag.getTagId().startsWith("PRIVATE_")) {
                continue;
            }
            String departmentSpaceId = "DEPARTMENT:" + tag.getTagId();
            if (ensureDepartmentSpaceRecord(departmentSpaceId, tag.getTagId(), tag.getName())) {
                createdSpaces++;
            }
        }

        int linkedDocuments = 0;
        if (fileUploadRepository != null) {
            for (FileUpload document : fileUploadRepository.findBySpaceIdMissing()) {
                document.setSpaceId(ensureSpaceForDocument(document));
                fileUploadRepository.save(document);
                linkedDocuments++;
            }
        }

        logger.info("Knowledge space backfill complete: createdSpaces={}, linkedDocuments={}", createdSpaces, linkedDocuments);
        return new BackfillResult(createdSpaces, linkedDocuments);
    }

    private boolean ensureSpaceRecord(String spaceId, FileUpload document) {
        if (knowledgeSpaceRepository == null || knowledgeSpaceRepository.existsBySpaceId(spaceId)) {
            return false;
        }

        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(spaceId);
        if ("PUBLIC".equals(spaceId)) {
            space.setType(KnowledgeSpace.SpaceType.PUBLIC);
            space.setName("公共知识库");
        } else if (PRIVATE_SPACE_ID.equals(spaceId)) {
            space.setType(KnowledgeSpace.SpaceType.PRIVATE);
            space.setName("个人知识库");
        } else if (document != null) {
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
        } else if (spaceId.startsWith("DEPARTMENT:")) {
            String departmentId = spaceId.substring("DEPARTMENT:".length());
            space.setType(KnowledgeSpace.SpaceType.DEPARTMENT);
            space.setDepartmentId(departmentId);
            space.setName(departmentName(departmentId) + "知识库");
        } else {
            return false;
        }
        knowledgeSpaceRepository.save(space);
        return true;
    }

    private boolean ensureDepartmentSpaceRecord(String spaceId, String departmentId, String departmentName) {
        if (knowledgeSpaceRepository.existsBySpaceId(spaceId)) {
            return false;
        }
        KnowledgeSpace space = new KnowledgeSpace();
        space.setSpaceId(spaceId);
        space.setType(KnowledgeSpace.SpaceType.DEPARTMENT);
        space.setDepartmentId(departmentId);
        space.setName((departmentName == null || departmentName.isBlank() ? departmentId : departmentName) + "知识库");
        knowledgeSpaceRepository.save(space);
        return true;
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

    public record BackfillResult(int createdSpaces, int linkedDocuments) {
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
