package com.yuki.enterprise_private_rag_qa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.client.RagLlmClient;
import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TrainingQuizService {

    private static final int DEFAULT_QUESTION_COUNT = 8;
    private static final int MAX_QUESTION_COUNT = 20;
    private static final List<String> SUPPORTED_QUESTION_TYPES = List.of("single_choice", "multiple_choice");

    private final TrainingMaterialService trainingMaterialService;
    private final RagLlmClient ragLlmClient;
    private final ObjectMapper objectMapper;

    public TrainingQuizService(TrainingMaterialService trainingMaterialService,
                               RagLlmClient ragLlmClient,
                               ObjectMapper objectMapper) {
        this.trainingMaterialService = trainingMaterialService;
        this.ragLlmClient = ragLlmClient;
        this.objectMapper = objectMapper;
    }

    public QuizGenerationResult generate(String userId, String orgTags, QuizGenerationRequest request) {
        QuizGenerationRequest normalized = normalize(request);
        TrainingMaterialService.SourceBundle sourceBundle = trainingMaterialService.build(
                userId,
                orgTags,
                normalized.knowledgeScope(),
                normalized.departmentId()
        );

        String raw = callModel(normalized, sourceBundle);
        JsonNode parsed = parseModelJson(raw);
        List<Map<String, Object>> questions = toQuestions(parsed);

        if (questions.isEmpty()) {
            questions = fallbackQuestions(normalized, sourceBundle);
        }

        String title = parsed != null && parsed.hasNonNull("title")
                ? parsed.path("title").asText()
                : buildTitle(normalized);

        return new QuizGenerationResult(
                title,
                normalized.knowledgeScope(),
                normalized.departmentId(),
                normalized.difficulty(),
                questions.size(),
                questions,
                raw,
                sourceBundle.sources(),
                LocalDateTime.now()
        );
    }

    private QuizGenerationRequest normalize(QuizGenerationRequest request) {
        String scope = trainingMaterialService.normalizeScope(request == null ? null : request.knowledgeScope());
        int count = request == null || request.questionCount() == null
                ? DEFAULT_QUESTION_COUNT
                : Math.max(1, Math.min(MAX_QUESTION_COUNT, request.questionCount()));
        String difficulty = request == null || request.difficulty() == null || request.difficulty().isBlank()
                ? "混合"
                : request.difficulty().trim();
        List<String> types = request == null || request.questionTypes() == null || request.questionTypes().isEmpty()
                ? List.of("single_choice", "multiple_choice")
                : request.questionTypes().stream()
                        .filter(type -> type != null && !type.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        if (types.isEmpty() || !SUPPORTED_QUESTION_TYPES.containsAll(types)) {
            throw new CustomException("题型暂只支持 single_choice、multiple_choice", HttpStatus.BAD_REQUEST);
        }
        return new QuizGenerationRequest(scope, request == null ? null : request.departmentId(), count, difficulty, types);
    }

    private String callModel(QuizGenerationRequest request, TrainingMaterialService.SourceBundle sourceBundle) {
        String system = "你是企业培训题库设计专家。只根据用户提供的知识库材料出题，禁止编造制度、金额、时间、责任人。"
                + "输出必须是严格 JSON，不要 Markdown，不要代码块。";
        String user = """
                请根据下面知识库材料生成培训题库。

                要求：
                - 题目数量：%d
                - 难度：%s
                - 题型范围：%s
                - 只能生成单选题或多选题，不要生成判断题、简答题、填空题。
                - 每题必须给出 4 个选项、答案、解析、来源文件名。
                - 单选题 type 使用 single_choice，answer 使用单元素数组，例如 ["A"]。
                - 多选题 type 使用 multiple_choice，answer 使用多元素数组，例如 ["A","C"]。
                - options 使用数组，每个选项以 A. / B. / C. / D. 开头。
                - JSON 格式：
                  {"title":"...","questions":[{"type":"single_choice","difficulty":"基础","question":"...","options":["A. ...","B. ...","C. ...","D. ..."],"answer":["A"],"explanation":"...","sourceFile":"..."}]}

                知识库材料：
                %s
                """.formatted(request.questionCount(), request.difficulty(), request.questionTypes(), sourceBundle.content());
        return ragLlmClient.chatSync(system, user, 0.2d);
    }

    private JsonNode parseModelJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> toQuestions(JsonNode parsed) {
        if (parsed == null || !parsed.path("questions").isArray()) {
            return List.of();
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        for (JsonNode node : parsed.path("questions")) {
            Map<String, Object> item = new LinkedHashMap<>();
            String type = node.path("type").asText("single_choice");
            if (!SUPPORTED_QUESTION_TYPES.contains(type)) {
                type = "single_choice";
            }
            item.put("type", type);
            item.put("difficulty", node.path("difficulty").asText("基础"));
            item.put("question", node.path("question").asText(""));
            item.put("options", toStringList(node.path("options")));
            item.put("answer", toAnswerList(node.path("answer")));
            item.put("explanation", node.path("explanation").asText(""));
            item.put("sourceFile", node.path("sourceFile").asText(""));
            if (!String.valueOf(item.get("question")).isBlank()
                    && item.get("options") instanceof List<?> options
                    && options.size() >= 2
                    && item.get("answer") instanceof List<?> answers
                    && !answers.isEmpty()) {
                questions.add(item);
            }
        }
        return questions;
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
    }

    private List<String> toAnswerList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            return toStringList(node).stream()
                    .map(this::normalizeChoiceKey)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }
        String raw = node.asText("");
        if (raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split("[,，、/\\s]+"))
                .map(this::normalizeChoiceKey)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeChoiceKey(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        char first = Character.toUpperCase(trimmed.charAt(0));
        if (first >= 'A' && first <= 'Z') {
            return String.valueOf(first);
        }
        return trimmed;
    }

    private List<Map<String, Object>> fallbackQuestions(QuizGenerationRequest request, TrainingMaterialService.SourceBundle sourceBundle) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < Math.min(request.questionCount(), sourceBundle.sources().size()); i++) {
            Map<String, Object> source = sourceBundle.sources().get(i);
            String fileName = String.valueOf(source.get("fileName"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "single_choice");
            item.put("difficulty", request.difficulty());
            item.put("question", "关于《" + fileName + "》的培训学习，以下哪项做法最符合要求？");
            item.put("options", List.of(
                    "A. 以当前有效文件和来源片段为准",
                    "B. 只按照个人经验执行",
                    "C. 忽略文件的生效和废止时间",
                    "D. 无需查看来源即可执行"
            ));
            item.put("answer", List.of("A"));
            item.put("explanation", "模型未返回可解析 JSON，系统生成了兜底题目。");
            item.put("sourceFile", fileName);
            questions.add(item);
        }
        return questions;
    }

    private String buildTitle(QuizGenerationRequest request) {
        return ("PUBLIC".equals(request.knowledgeScope()) ? "公共知识库" : "部门知识库") + "培训题库";
    }

    public record QuizGenerationRequest(
            String knowledgeScope,
            String departmentId,
            Integer questionCount,
            String difficulty,
            List<String> questionTypes
    ) {
    }

    public record QuizGenerationResult(
            String title,
            String knowledgeScope,
            String departmentId,
            String difficulty,
            int questionCount,
            List<Map<String, Object>> questions,
            String rawContent,
            List<Map<String, Object>> sources,
            LocalDateTime generatedAt
    ) {
    }
}
