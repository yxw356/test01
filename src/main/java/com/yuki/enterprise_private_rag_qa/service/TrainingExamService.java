package com.yuki.enterprise_private_rag_qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.TrainingExamAttempt;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.TrainingExamAttemptRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TrainingExamService {

    private final TrainingExamAttemptRepository repository;
    private final DocumentPermissionService permissionService;
    private final ObjectMapper objectMapper;

    public TrainingExamService(TrainingExamAttemptRepository repository,
                               DocumentPermissionService permissionService,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public SubmitResult submit(String userId, SubmitRequest request) {
        User user = permissionService.requireUser(userId);
        SubmitRequest normalized = normalizeSubmitRequest(user, request);
        List<QuestionReview> reviews = grade(normalized.questions(), normalized.answers());
        int total = reviews.size();
        int correct = (int) reviews.stream().filter(QuestionReview::correct).count();
        double score = total == 0 ? 0.0d : Math.round(correct * 10000.0d / total) / 100.0d;

        TrainingExamAttempt attempt = new TrainingExamAttempt();
        attempt.setUserId(String.valueOf(user.getId()));
        attempt.setUsername(user.getUsername());
        attempt.setKnowledgeScope(normalized.knowledgeScope());
        attempt.setDepartmentId(normalized.departmentId());
        attempt.setTitle(normalized.title());
        attempt.setScore(score);
        attempt.setCorrectCount(correct);
        attempt.setTotalCount(total);
        attempt.setDurationSeconds(normalized.durationSeconds());
        attempt.setQuestionsJson(writeJson(normalized.questions()));
        attempt.setAnswersJson(writeJson(normalized.answers()));
        attempt.setReviewJson(writeJson(reviews));
        attempt.setSourcesJson(writeJson(normalized.sources()));
        repository.save(attempt);

        return new SubmitResult(
                attempt.getId(),
                attempt.getTitle(),
                attempt.getKnowledgeScope(),
                attempt.getDepartmentId(),
                score,
                correct,
                total,
                attempt.getDurationSeconds(),
                reviews,
                attempt.getCreatedAt() == null ? LocalDateTime.now() : attempt.getCreatedAt()
        );
    }

    public List<RankingRow> ranking(String userId, String knowledgeScope, String departmentId, Integer limit) {
        User user = permissionService.requireUser(userId);
        String scope = normalizeScope(knowledgeScope);
        String dept = normalizeRankingDepartment(user, scope, departmentId);
        int size = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        List<TrainingExamAttempt> attempts = "DEPARTMENT".equals(scope)
                ? repository.findByKnowledgeScopeAndDepartmentIdOrderByScoreDescDurationSecondsAscCreatedAtAsc(scope, dept, PageRequest.of(0, size))
                : repository.findByKnowledgeScopeOrderByScoreDescDurationSecondsAscCreatedAtAsc(scope, PageRequest.of(0, size));

        List<RankingRow> rows = new ArrayList<>();
        for (int i = 0; i < attempts.size(); i++) {
            TrainingExamAttempt attempt = attempts.get(i);
            rows.add(new RankingRow(
                    i + 1,
                    attempt.getId(),
                    attempt.getUsername(),
                    attempt.getKnowledgeScope(),
                    attempt.getDepartmentId(),
                    attempt.getScore(),
                    attempt.getCorrectCount(),
                    attempt.getTotalCount(),
                    attempt.getDurationSeconds(),
                    attempt.getCreatedAt()
            ));
        }
        return rows;
    }

    private SubmitRequest normalizeSubmitRequest(User user, SubmitRequest request) {
        if (request == null || request.questions() == null || request.questions().isEmpty()) {
            throw new CustomException("请先生成题目再提交", HttpStatus.BAD_REQUEST);
        }
        String scope = normalizeScope(request.knowledgeScope());
        String departmentId = request.departmentId();
        if ("DEPARTMENT".equals(scope)) {
            departmentId = requireVisibleDepartment(user, departmentId);
        }
        String title = request.title() == null || request.title().isBlank() ? "部门培训考试" : request.title().trim();
        return new SubmitRequest(
                title,
                scope,
                departmentId,
                request.questions(),
                request.answers() == null ? Map.of() : request.answers(),
                request.sources() == null ? List.of() : request.sources(),
                request.durationSeconds()
        );
    }

    private String normalizeScope(String knowledgeScope) {
        String scope = knowledgeScope == null || knowledgeScope.isBlank() ? "PUBLIC" : knowledgeScope.trim().toUpperCase();
        if (!"PUBLIC".equals(scope) && !"DEPARTMENT".equals(scope)) {
            throw new CustomException("考试范围暂只支持公共知识库或部门知识库", HttpStatus.BAD_REQUEST);
        }
        return scope;
    }

    private String normalizeRankingDepartment(User user, String scope, String departmentId) {
        if (!"DEPARTMENT".equals(scope)) {
            return null;
        }
        return requireVisibleDepartment(user, departmentId);
    }

    private String requireVisibleDepartment(User user, String departmentId) {
        List<String> departments = permissionService.effectiveDepartmentIds(user);
        String requested = departmentId == null || departmentId.isBlank()
                ? user.getPrimaryOrg()
                : departmentId.trim();
        if ((requested == null || requested.isBlank()) && !departments.isEmpty()) {
            requested = departments.get(0);
        }
        if (requested == null || requested.isBlank()) {
            throw new CustomException("当前用户没有所属部门，无法参与部门考试", HttpStatus.BAD_REQUEST);
        }
        String resolved = requested;
        if (!user.isSuperAdmin() && departments.stream().noneMatch(item -> item.equalsIgnoreCase(resolved))) {
            throw new CustomException("无权访问该部门考试", HttpStatus.FORBIDDEN);
        }
        return resolved;
    }

    private List<QuestionReview> grade(List<Map<String, Object>> questions, Map<String, List<String>> answers) {
        List<QuestionReview> reviews = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            String key = String.valueOf(i);
            List<String> expected = normalizeAnswers(question.get("answer"));
            List<String> actual = normalizeAnswers(answers.get(key));
            boolean correct = !expected.isEmpty() && new LinkedHashSet<>(expected).equals(new LinkedHashSet<>(actual));
            reviews.add(new QuestionReview(
                    i,
                    String.valueOf(question.getOrDefault("type", "single_choice")),
                    String.valueOf(question.getOrDefault("question", "")),
                    expected,
                    actual,
                    correct,
                    String.valueOf(question.getOrDefault("explanation", "")),
                    String.valueOf(question.getOrDefault("sourceFile", ""))
            ));
        }
        return reviews;
    }

    private List<String> normalizeAnswers(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> rawValues = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> rawValues.add(String.valueOf(item)));
        } else {
            rawValues.add(String.valueOf(value));
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawValues) {
            for (String part : raw.split("[,，、/\\s]+")) {
                String valuePart = part.trim();
                if (valuePart.isEmpty()) {
                    continue;
                }
                char first = Character.toUpperCase(valuePart.charAt(0));
                normalized.add(first >= 'A' && first <= 'Z' ? String.valueOf(first) : valuePart);
            }
        }
        return List.copyOf(normalized);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public record SubmitRequest(
            String title,
            String knowledgeScope,
            String departmentId,
            List<Map<String, Object>> questions,
            Map<String, List<String>> answers,
            List<Map<String, Object>> sources,
            Integer durationSeconds
    ) {
    }

    public record QuestionReview(
            int index,
            String type,
            String question,
            List<String> expected,
            List<String> actual,
            boolean correct,
            String explanation,
            String sourceFile
    ) {
    }

    public record SubmitResult(
            Long attemptId,
            String title,
            String knowledgeScope,
            String departmentId,
            double score,
            int correctCount,
            int totalCount,
            Integer durationSeconds,
            List<QuestionReview> reviews,
            LocalDateTime submittedAt
    ) {
    }

    public record RankingRow(
            int rank,
            Long attemptId,
            String username,
            String knowledgeScope,
            String departmentId,
            double score,
            int correctCount,
            int totalCount,
            Integer durationSeconds,
            LocalDateTime submittedAt
    ) {
    }
}
