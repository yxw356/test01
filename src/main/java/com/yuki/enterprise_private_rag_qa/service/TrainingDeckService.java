package com.yuki.enterprise_private_rag_qa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.client.RagLlmClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TrainingDeckService {

    private static final int DEFAULT_SLIDE_COUNT = 8;
    private static final int MAX_SLIDE_COUNT = 18;

    private final TrainingMaterialService trainingMaterialService;
    private final RagLlmClient ragLlmClient;
    private final ObjectMapper objectMapper;

    public TrainingDeckService(TrainingMaterialService trainingMaterialService,
                               RagLlmClient ragLlmClient,
                               ObjectMapper objectMapper) {
        this.trainingMaterialService = trainingMaterialService;
        this.ragLlmClient = ragLlmClient;
        this.objectMapper = objectMapper;
    }

    public DeckGenerationResult generate(String userId, String orgTags, DeckGenerationRequest request) {
        DeckGenerationRequest normalized = normalize(request);
        TrainingMaterialService.SourceBundle sourceBundle = trainingMaterialService.build(
                userId,
                orgTags,
                normalized.knowledgeScope(),
                normalized.departmentId()
        );

        String raw = callModel(normalized, sourceBundle);
        JsonNode parsed = parseModelJson(raw);
        List<DeckSlide> slides = toSlides(parsed);
        if (slides.isEmpty()) {
            slides = fallbackSlides(normalized, sourceBundle);
        }

        String title = parsed != null && parsed.hasNonNull("title")
                ? parsed.path("title").asText()
                : buildTitle(normalized);
        String audience = parsed != null && parsed.hasNonNull("audience")
                ? parsed.path("audience").asText()
                : normalized.audience();

        return new DeckGenerationResult(
                title,
                audience,
                normalized.knowledgeScope(),
                normalized.departmentId(),
                normalized.tone(),
                slides.size(),
                slides,
                raw,
                sourceBundle.sources(),
                LocalDateTime.now()
        );
    }

    private DeckGenerationRequest normalize(DeckGenerationRequest request) {
        String scope = trainingMaterialService.normalizeScope(request == null ? null : request.knowledgeScope());
        int slideCount = request == null || request.slideCount() == null
                ? DEFAULT_SLIDE_COUNT
                : Math.max(3, Math.min(MAX_SLIDE_COUNT, request.slideCount()));
        String audience = request == null || request.audience() == null || request.audience().isBlank()
                ? "部门员工"
                : request.audience().trim();
        String tone = request == null || request.tone() == null || request.tone().isBlank()
                ? "正式清晰"
                : request.tone().trim();
        return new DeckGenerationRequest(scope, request == null ? null : request.departmentId(), slideCount, audience, tone);
    }

    private String callModel(DeckGenerationRequest request, TrainingMaterialService.SourceBundle sourceBundle) {
        String system = "你是企业内训课件设计专家。只根据用户提供的知识库材料生成培训课件，禁止编造制度、金额、时间、责任人。"
                + "输出必须是严格 JSON，不要 Markdown，不要代码块。";
        String user = """
                请根据下面知识库材料生成部门培训 PPT 大纲。

                要求：
                - 幻灯片页数：%d
                - 受众：%s
                - 风格：%s
                - 每页包含 title、bullets、speakerNotes、sourceFiles。
                - bullets 每页 3-5 条，适合直接放到 PPT。
                - speakerNotes 给讲师讲解重点和提醒。
                - 必须包含制度适用边界、关键流程、注意事项、常见误区或考核提醒。
                - JSON 格式：
                  {"title":"...","audience":"...","slides":[{"title":"...","bullets":["..."],"speakerNotes":"...","sourceFiles":["..."]}]}

                知识库材料：
                %s
                """.formatted(request.slideCount(), request.audience(), request.tone(), sourceBundle.content());
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

    private List<DeckSlide> toSlides(JsonNode parsed) {
        if (parsed == null || !parsed.path("slides").isArray()) {
            return List.of();
        }
        List<DeckSlide> slides = new ArrayList<>();
        int index = 1;
        for (JsonNode node : parsed.path("slides")) {
            DeckSlide slide = new DeckSlide(
                    index++,
                    node.path("title").asText(""),
                    toStringList(node.path("bullets")),
                    node.path("speakerNotes").asText(""),
                    toStringList(node.path("sourceFiles"))
            );
            if (!slide.title().isBlank()) {
                slides.add(slide);
            }
        }
        return slides;
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

    private List<DeckSlide> fallbackSlides(DeckGenerationRequest request,
                                           TrainingMaterialService.SourceBundle sourceBundle) {
        List<DeckSlide> slides = new ArrayList<>();
        slides.add(slide(1, buildTitle(request), List.of(
                "培训对象：" + request.audience(),
                "本课件根据当前知识库有效文件自动生成",
                "请以来源文件原文作为最终制度依据"
        ), "介绍培训范围、适用文件和学习目标。", sourceBundle));

        int index = 2;
        for (Map<String, Object> source : sourceBundle.sources().subList(0, Math.min(5, sourceBundle.sources().size()))) {
            String fileName = String.valueOf(source.get("fileName"));
            slides.add(slide(index++, fileName, List.of(
                    "提炼该文件中的关键制度要求",
                    "说明适用范围、生效边界和执行责任",
                    "结合部门场景讲解常见问题"
            ), "模型未返回可解析 JSON，系统生成了兜底页。讲师应打开来源文件核对细节。", sourceBundle));
        }
        return slides;
    }

    private DeckSlide slide(int index, String title, List<String> bullets, String speakerNotes,
                            TrainingMaterialService.SourceBundle sourceBundle) {
        return new DeckSlide(
                index,
                title,
                bullets,
                speakerNotes,
                sourceBundle.sources().stream().map(source -> String.valueOf(source.get("fileName"))).limit(3).toList()
        );
    }

    private String buildTitle(DeckGenerationRequest request) {
        return ("PUBLIC".equals(request.knowledgeScope()) ? "公共知识库" : "部门知识库") + "培训课件";
    }

    public record DeckGenerationRequest(
            String knowledgeScope,
            String departmentId,
            Integer slideCount,
            String audience,
            String tone
    ) {
    }

    public record DeckGenerationResult(
            String title,
            String audience,
            String knowledgeScope,
            String departmentId,
            String tone,
            int slideCount,
            List<DeckSlide> slides,
            String rawContent,
            List<Map<String, Object>> sources,
            LocalDateTime generatedAt
    ) {
    }

    public record DeckSlide(
            int index,
            String title,
            List<String> bullets,
            String speakerNotes,
            List<String> sourceFiles
    ) {
    }
}
