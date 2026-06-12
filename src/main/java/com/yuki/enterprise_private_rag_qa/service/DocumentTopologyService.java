package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeCase;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeFaq;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocumentTopologyService {

    private final DocumentPermissionService permissionService;
    private final KnowledgeFaqService faqService;
    private final KnowledgeCaseService caseService;

    public DocumentTopologyService(DocumentPermissionService permissionService,
                                   KnowledgeFaqService faqService,
                                   KnowledgeCaseService caseService) {
        this.permissionService = permissionService;
        this.faqService = faqService;
        this.caseService = caseService;
    }

    public Map<String, Object> buildTopology(List<FileUpload> files) {
        return buildTopology(files, null);
    }

    public Map<String, Object> buildTopology(List<FileUpload> files, String userId) {
        Map<String, FileUpload> byMd5 = files.stream()
                .filter(file -> file.getFileMd5() != null)
                .collect(Collectors.toMap(FileUpload::getFileMd5, file -> file, (left, right) -> left));
        List<Map<String, Object>> nodes = new ArrayList<>(files.stream()
                .map(this::nodeDto)
                .toList());
        List<Map<String, Object>> edges = buildEdges(files, byMd5.keySet());
        if (userId != null && !userId.isBlank()) {
            addGovernanceNodes(userId, nodes, edges, files);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("nodes", nodes);
        data.put("edges", edges);
        data.put("summary", Map.of(
                "nodeCount", nodes.size(),
                "edgeCount", edges.size(),
                "supersedesEdgeCount", edges.stream().filter(edge -> "SUPERSEDES".equals(edge.get("type"))).count(),
                "departmentEdgeCount", edges.stream().filter(edge -> "SAME_DEPARTMENT".equals(edge.get("type"))).count(),
                "categoryEdgeCount", edges.stream().filter(edge -> "SAME_CATEGORY".equals(edge.get("type"))).count(),
                "governanceNodeCount", nodes.stream().filter(node -> !"DOCUMENT".equals(node.get("nodeType"))).count(),
                "activeCount", files.stream().filter(this::isActive).count(),
                "expiredCount", files.stream().filter(this::isExpired).count(),
                "auditIssueCount", files.stream().filter(this::hasAuditIssue).count()
        ));
        return data;
    }

    private void addGovernanceNodes(String userId,
                                    List<Map<String, Object>> nodes,
                                    List<Map<String, Object>> edges,
                                    List<FileUpload> files) {
        Map<String, String> representativeByDepartment = files.stream()
                .filter(file -> file.getFileMd5() != null)
                .collect(Collectors.toMap(
                        file -> {
                            String department = permissionService.effectiveDepartmentId(file);
                            return department == null || department.isBlank() ? "PUBLIC" : department;
                        },
                        FileUpload::getFileMd5,
                        (left, right) -> left
                ));
        for (KnowledgeFaq faq : faqService.listVisible(userId).stream().limit(20).toList()) {
            String id = "FAQ:" + faq.getId();
            nodes.add(governanceNode(id, compactName(faq.getQuestion()), "FAQ", faq.getDepartmentId(), faq.getKnowledgeScope().name(), 28));
            addGovernanceEdge(edges, id, representativeByDepartment.get(groupKey(faq.getDepartmentId(), faq.getKnowledgeScope())), "ANSWERED_BY", "问答关联");
        }
        for (KnowledgeCase item : caseService.listVisible(userId).stream().limit(20).toList()) {
            String id = "CASE:" + item.getId();
            nodes.add(governanceNode(id, compactName(item.getTitle()), "CASE", item.getDepartmentId(), item.getKnowledgeScope().name(), 30));
            addGovernanceEdge(edges, id, representativeByDepartment.get(groupKey(item.getDepartmentId(), item.getKnowledgeScope())), "CASE_SUPPORTS", "案例关联");
        }
    }

    private Map<String, Object> governanceNode(String id,
                                               String label,
                                               String nodeType,
                                               String departmentId,
                                               String knowledgeScope,
                                               int size) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("fileMd5", id);
        node.put("fileName", label);
        node.put("label", label);
        node.put("nodeType", nodeType);
        node.put("group", groupKey(departmentId, FileUpload.KnowledgeScope.valueOf(knowledgeScope)));
        node.put("departmentId", departmentId);
        node.put("knowledgeScope", knowledgeScope);
        node.put("riskLevel", "NONE");
        node.put("retrievable", true);
        node.put("symbolSize", size);
        return node;
    }

    private void addGovernanceEdge(List<Map<String, Object>> edges, String source, String target, String type, String label) {
        if (isBlank(source) || isBlank(target)) {
            return;
        }
        Map<String, Object> edge = new HashMap<>();
        edge.put("id", source + "->" + target + ":" + type);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", type);
        edge.put("label", label);
        edge.put("weight", 1);
        edge.put("description", label);
        edge.put("curveness", 0.12d);
        edges.add(edge);
    }

    private String groupKey(String departmentId, FileUpload.KnowledgeScope scope) {
        if (scope == FileUpload.KnowledgeScope.PUBLIC) {
            return "PUBLIC";
        }
        return departmentId == null || departmentId.isBlank() ? "UNKNOWN_DEPARTMENT" : departmentId;
    }

    private Map<String, Object> nodeDto(FileUpload file) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", file.getFileMd5());
        node.put("fileMd5", file.getFileMd5());
        node.put("fileName", file.getFileName());
        node.put("label", compactName(file.getFileName()));
        node.put("nodeType", "DOCUMENT");
        node.put("versionNo", file.getVersionNo());
        node.put("knowledgeScope", permissionService.effectiveScope(file).name());
        node.put("departmentId", permissionService.effectiveDepartmentId(file));
        node.put("group", graphGroup(file));
        node.put("categoryId", file.getCategoryId());
        node.put("categoryName", file.getCategoryName());
        node.put("lifecycleStatus", file.getLifecycleStatus().name());
        node.put("policyAuditStatus", file.getPolicyAuditStatus().name());
        node.put("effectiveAt", file.getEffectiveAt());
        node.put("abolishedAt", file.getAbolishedAt());
        node.put("publishedAt", file.getPublishedAt());
        node.put("retrievable", isActive(file) && !hasAuditIssue(file));
        node.put("supersedesFileMd5", file.getSupersedesFileMd5());
        node.put("supersededByFileMd5", file.getSupersededByFileMd5());
        node.put("riskLevel", riskLevel(file));
        node.put("symbolSize", symbolSize(file));
        int seed = Math.abs((file.getFileMd5() == null ? file.getFileName() : file.getFileMd5()).hashCode());
        node.put("x", (seed % 900) - 450);
        node.put("y", ((seed / 17) % 520) - 260);
        return node;
    }

    private List<Map<String, Object>> buildEdges(List<FileUpload> files, Set<String> accessibleMd5s) {
        Map<String, Map<String, Object>> edges = new LinkedHashMap<>();
        for (FileUpload file : files) {
            addEdge(edges, relationEdge(file.getFileMd5(), file.getSupersedesFileMd5(), "SUPERSEDES", "替代", 3, accessibleMd5s));
            addEdge(edges, relationEdge(file.getSupersededByFileMd5(), file.getFileMd5(), "SUPERSEDES", "替代", 3, accessibleMd5s));
        }
        addGroupedEdges(edges, files, "SAME_DEPARTMENT", "同部门", 1, file -> permissionService.effectiveDepartmentId(file));
        addGroupedEdges(edges, files, "SAME_CATEGORY", "同分类", 2, file -> file.getCategoryId() == null ? null : String.valueOf(file.getCategoryId()));
        addGroupedEdges(edges, files, "SAME_LIFECYCLE", "同生命周期", 1, file -> file.getLifecycleStatus() == null ? null : file.getLifecycleStatus().name());
        return new ArrayList<>(edges.values());
    }

    private void addGroupedEdges(Map<String, Map<String, Object>> edges,
                                 List<FileUpload> files,
                                 String type,
                                 String label,
                                 int weight,
                                 java.util.function.Function<FileUpload, String> classifier) {
        Map<String, List<FileUpload>> groups = files.stream()
                .filter(file -> file.getFileMd5() != null)
                .collect(Collectors.groupingBy(file -> {
                    String key = classifier.apply(file);
                    return key == null || key.isBlank() ? "__EMPTY__" : key;
                }));
        for (Map.Entry<String, List<FileUpload>> entry : groups.entrySet()) {
            if ("__EMPTY__".equals(entry.getKey()) || entry.getValue().size() < 2) {
                continue;
            }
            List<FileUpload> ordered = entry.getValue().stream()
                    .sorted(java.util.Comparator.comparing(
                            (FileUpload file) -> file.getPublishedAt() != null ? file.getPublishedAt() : file.getCreatedAt(),
                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                    .toList();
            int maxEdgesPerGroup = 8;
            for (int i = 1; i < ordered.size() && i <= maxEdgesPerGroup; i++) {
                addEdge(edges, relationEdge(
                        ordered.get(i - 1).getFileMd5(),
                        ordered.get(i).getFileMd5(),
                        type,
                        label,
                        weight,
                        ordered.stream().map(FileUpload::getFileMd5).collect(Collectors.toSet())
                ));
            }
        }
    }

    private void addEdge(Map<String, Map<String, Object>> edges, Map<String, Object> edge) {
        if (edge != null) {
            edges.putIfAbsent(String.valueOf(edge.get("id")), edge);
        }
    }

    private Map<String, Object> relationEdge(String sourceMd5, String targetMd5, String type, String label, int weight, Set<String> accessibleMd5s) {
        if (isBlank(sourceMd5) || isBlank(targetMd5)) {
            return null;
        }
        String source = sourceMd5.trim();
        String target = targetMd5.trim();
        if (source.equals(target) || !accessibleMd5s.contains(source) || !accessibleMd5s.contains(target)) {
            return null;
        }
        Map<String, Object> edge = new HashMap<>();
        edge.put("id", source + "->" + target + ":" + type);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", type);
        edge.put("label", label);
        edge.put("weight", weight);
        edge.put("description", label + "关系：" + source + " -> " + target);
        edge.put("curveness", "SUPERSEDES".equals(type) ? 0.18d : 0.08d);
        return edge;
    }

    private String compactName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "未命名文件";
        }
        String name = fileName.replaceFirst("\\.[^.]+$", "");
        return name.length() <= 18 ? name : name.substring(0, 17) + "…";
    }

    private String graphGroup(FileUpload file) {
        FileUpload.KnowledgeScope scope = permissionService.effectiveScope(file);
        if (scope == FileUpload.KnowledgeScope.PUBLIC || file.isPublic()) {
            return "PUBLIC";
        }
        String departmentId = permissionService.effectiveDepartmentId(file);
        if (departmentId == null || departmentId.isBlank()) {
            return "UNKNOWN_DEPARTMENT";
        }
        return departmentId;
    }

    private String riskLevel(FileUpload file) {
        if (file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.REJECT
                || file.getLifecycleStatus() == FileUpload.LifecycleStatus.AUDIT_REJECTED) {
            return "HIGH";
        }
        if (file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.NEED_MANUAL_REVIEW
                || file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.PASS_WITH_WARNINGS
                || isExpired(file)) {
            return "MEDIUM";
        }
        if (!isActive(file)) {
            return "LOW";
        }
        return "NONE";
    }

    private int symbolSize(FileUpload file) {
        int size = 34;
        if (file.getSupersedesFileMd5() != null && !file.getSupersedesFileMd5().isBlank()) {
            size += 8;
        }
        if (file.getSupersededByFileMd5() != null && !file.getSupersededByFileMd5().isBlank()) {
            size += 6;
        }
        if (hasAuditIssue(file)) {
            size += 10;
        }
        return Math.min(size, 62);
    }

    private boolean isActive(FileUpload file) {
        LocalDateTime now = LocalDateTime.now();
        boolean statusActive = file.getLifecycleStatus() == FileUpload.LifecycleStatus.ACTIVE;
        boolean afterEffective = file.getEffectiveAt() == null || !file.getEffectiveAt().isAfter(now);
        boolean beforeAbolished = file.getAbolishedAt() == null || file.getAbolishedAt().isAfter(now);
        return statusActive && afterEffective && beforeAbolished;
    }

    private boolean isExpired(FileUpload file) {
        LocalDateTime now = LocalDateTime.now();
        return file.getLifecycleStatus() == FileUpload.LifecycleStatus.EXPIRED
                || file.getLifecycleStatus() == FileUpload.LifecycleStatus.REVOKED
                || file.getLifecycleStatus() == FileUpload.LifecycleStatus.SUPERSEDED
                || (file.getAbolishedAt() != null && !file.getAbolishedAt().isAfter(now));
    }

    private boolean hasAuditIssue(FileUpload file) {
        return file.getLifecycleStatus() == FileUpload.LifecycleStatus.AUDIT_REJECTED
                || file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.REJECT
                || file.getPolicyAuditStatus() == FileUpload.PolicyAuditStatus.NEED_MANUAL_REVIEW;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
