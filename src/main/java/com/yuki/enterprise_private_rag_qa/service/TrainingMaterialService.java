package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.DocumentVector;
import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.DocumentVectorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TrainingMaterialService {

    private static final int MAX_FILES = 12;
    private static final int MAX_CHUNKS_PER_FILE = 4;
    private static final int MAX_SOURCE_CHARS = 24000;

    private final DocumentService documentService;
    private final DocumentPermissionService documentPermissionService;
    private final DocumentLifecycleService documentLifecycleService;
    private final DocumentVectorRepository documentVectorRepository;

    public TrainingMaterialService(DocumentService documentService,
                                   DocumentPermissionService documentPermissionService,
                                   DocumentLifecycleService documentLifecycleService,
                                   DocumentVectorRepository documentVectorRepository) {
        this.documentService = documentService;
        this.documentPermissionService = documentPermissionService;
        this.documentLifecycleService = documentLifecycleService;
        this.documentVectorRepository = documentVectorRepository;
    }

    public SourceBundle build(String userId, String orgTags, String knowledgeScope, String departmentId) {
        User user = documentPermissionService.requireUser(userId);
        String scope = normalizeScope(knowledgeScope);
        List<FileUpload> files = documentService.getAccessibleFiles(userId, orgTags).stream()
                .filter(file -> documentPermissionService.canView(user, file))
                .filter(file -> file.getEffectiveUploadStatus() == 1)
                .filter(file -> file.getIndexStatus() == FileIndexStatus.INDEXED)
                .filter(documentLifecycleService::isSearchable)
                .filter(file -> matchesScope(file, scope, departmentId))
                .sorted(Comparator.comparing(
                        (FileUpload file) -> file.getMergedAt() != null ? file.getMergedAt() : file.getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_FILES)
                .toList();

        if (files.isEmpty()) {
            throw new CustomException("当前范围内没有可用于生成培训内容的有效知识文件", HttpStatus.BAD_REQUEST);
        }

        SourceBundle bundle = buildSourceBundle(files);
        if (bundle.content().isBlank()) {
            throw new CustomException("当前范围内的文件还没有可用解析文本，无法生成培训内容", HttpStatus.BAD_REQUEST);
        }
        return bundle;
    }

    public String normalizeScope(String knowledgeScope) {
        String scope = knowledgeScope == null || knowledgeScope.isBlank()
                ? "PUBLIC"
                : knowledgeScope.trim().toUpperCase(Locale.ROOT);
        if (!"PUBLIC".equals(scope) && !"DEPARTMENT".equals(scope)) {
            throw new CustomException("培训内容生成暂只支持公共知识库或部门知识库", HttpStatus.BAD_REQUEST);
        }
        return scope;
    }

    private boolean matchesScope(FileUpload file, String requestedScope, String requestedDepartmentId) {
        FileUpload.KnowledgeScope scope = documentPermissionService.effectiveScope(file);
        if ("PUBLIC".equals(requestedScope)) {
            return scope == FileUpload.KnowledgeScope.PUBLIC || file.isPublic();
        }
        if (scope != FileUpload.KnowledgeScope.DEPARTMENT) {
            return false;
        }
        if (requestedDepartmentId == null || requestedDepartmentId.isBlank()) {
            return true;
        }
        String actual = documentPermissionService.effectiveDepartmentId(file);
        return actual != null && actual.equalsIgnoreCase(requestedDepartmentId.trim());
    }

    private SourceBundle buildSourceBundle(List<FileUpload> files) {
        StringBuilder content = new StringBuilder();
        List<Map<String, Object>> sources = new ArrayList<>();
        Set<String> seenText = new LinkedHashSet<>();

        for (FileUpload file : files) {
            List<DocumentVector> vectors = new ArrayList<>(documentVectorRepository.findByFileMd5(file.getFileMd5()));
            vectors.sort(Comparator.comparing(DocumentVector::getChunkId, Comparator.nullsLast(Integer::compareTo)));
            int usedChunks = 0;
            int before = content.length();
            for (DocumentVector vector : vectors) {
                String text = bestText(vector);
                if (text.isBlank() || !seenText.add(normalizeTextKey(text))) {
                    continue;
                }
                if (content.length() >= MAX_SOURCE_CHARS || usedChunks >= MAX_CHUNKS_PER_FILE) {
                    break;
                }
                int remaining = MAX_SOURCE_CHARS - content.length();
                if (remaining <= 0) {
                    break;
                }
                content.append("\n\n[文件：").append(file.getFileName()).append("]\n");
                content.append(text, 0, Math.min(text.length(), remaining));
                usedChunks++;
            }
            if (content.length() > before) {
                sources.add(Map.of(
                        "fileMd5", file.getFileMd5(),
                        "fileName", file.getFileName(),
                        "knowledgeScope", documentPermissionService.effectiveScope(file).name(),
                        "departmentId", documentPermissionService.effectiveDepartmentId(file),
                        "versionNo", file.getVersionNo() == null ? "" : file.getVersionNo()
                ));
            }
            if (content.length() >= MAX_SOURCE_CHARS) {
                break;
            }
        }
        return new SourceBundle(content.toString().trim(), sources);
    }

    private String bestText(DocumentVector vector) {
        String parent = vector.getParentTextContent();
        if (parent != null && !parent.isBlank()) {
            return parent.trim();
        }
        return vector.getTextContent() == null ? "" : vector.getTextContent().trim();
    }

    private String normalizeTextKey(String text) {
        String normalized = text.replaceAll("\\s+", "");
        return normalized.substring(0, Math.min(120, normalized.length()));
    }

    public record SourceBundle(String content, List<Map<String, Object>> sources) {
    }
}
