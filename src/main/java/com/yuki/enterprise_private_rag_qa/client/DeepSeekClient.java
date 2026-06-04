package com.yuki.enterprise_private_rag_qa.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DeepSeekClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final AiProperties aiProperties;
    private final boolean ragEnabled;
    private static final Logger logger = LoggerFactory.getLogger(DeepSeekClient.class);
    private static final int MAX_HISTORY_MESSAGES = 6;
    
    public DeepSeekClient(@Value("${deepseek.api.url}") String apiUrl,
                         @Value("${deepseek.api.key}") String apiKey,
                         @Value("${deepseek.api.model}") String model,
                         AiProperties aiProperties,
                         @Value("${rag.enabled:true}") boolean ragEnabled) {
        WebClient.Builder builder = WebClient.builder().baseUrl(apiUrl);
        
        // 只有当 API key 不为空时才添加 Authorization header
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        
        this.webClient = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.aiProperties = aiProperties;
        this.ragEnabled = ragEnabled;
    }
    
    public Disposable streamResponse(String userMessage,
                                     String context,
                                     List<Map<String, String>> history,
                                     Consumer<String> onChunk,
                                     Consumer<Throwable> onError,
                                     Runnable onComplete) {
        
        Map<String, Object> request = buildRequest(userMessage, context, history);
        
        ThinkingTagFilter thinkingTagFilter = new ThinkingTagFilter();

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                    chunk -> processChunk(chunk, content -> {
                        String visibleContent = thinkingTagFilter.filter(content);
                        if (!visibleContent.isEmpty()) {
                            onChunk.accept(visibleContent);
                        }
                    }),
                    onError,
                    onComplete
                );
    }
    
    private Map<String, Object> buildRequest(String userMessage, 
                                           String context,
                                           List<Map<String, String>> history) {
        logger.info("构建请求，用户消息：{}，上下文长度：{}，历史消息数：{}", 
                   userMessage, 
                   context != null ? context.length() : 0, 
                   history != null ? history.size() : 0);
        
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("model", model);
        request.put("messages", buildMessages(userMessage, context, history));
        request.put("stream", true);
        // 生成参数
        AiProperties.Generation gen = aiProperties.getGeneration();
        if (gen.getTemperature() != null) {
            request.put("temperature", gen.getTemperature());
        }
        if (gen.getTopP() != null) {
            request.put("top_p", gen.getTopP());
        }
        if (gen.getMaxTokens() != null) {
            request.put("max_tokens", gen.getMaxTokens());
        }
        if (!ragEnabled) {
            request.put("chat_template_kwargs", Map.of("enable_thinking", false));
        }
        return request;
    }
    
    private List<Map<String, String>> buildMessages(String userMessage,
                                                  String context,
                                                  List<Map<String, String>> history) {
        List<Map<String, String>> messages = new ArrayList<>();

        AiProperties.Prompt promptCfg = aiProperties.getPrompt();

        StringBuilder sysBuilder = new StringBuilder();
        String rules = promptCfg.getRules();
        if (rules != null) {
            sysBuilder.append(rules).append("\n");
        }
        sysBuilder.append("本轮参考信息会随当前用户问题一起给出。")
                .append("回答知识库相关问题时，以本轮参考信息为准，不要沿用历史中的旧检索结论。")
                .append("用户提出身份、寒暄、能力介绍、使用帮助等常规问题时，可以直接自然回答。");

        String systemContent = sysBuilder.toString();
        messages.add(Map.of(
            "role", "system",
            "content", systemContent
        ));
        logger.debug("添加了系统消息，长度: {}", systemContent.length());

        messages.addAll(sanitizeHistory(history));

        messages.add(Map.of(
            "role", "user",
            "content", buildCurrentTurnContent(userMessage, context, promptCfg)
        ));

        return messages;
    }

    private String buildCurrentTurnContent(String userMessage, String context, AiProperties.Prompt promptCfg) {
        if (!ragEnabled) {
            return "请直接回答用户问题；如果问题与本地知识库资料有关但当前没有检索上下文，请说明当前为本地纯聊天模式。"
                    + "\n\n用户问题：\n" + userMessage;
        }

        String refStart = promptCfg.getRefStart() != null ? promptCfg.getRefStart() : "<<REF>>";
        String refEnd = promptCfg.getRefEnd() != null ? promptCfg.getRefEnd() : "<<END>>";
        String noResult = promptCfg.getNoResultText() != null ? promptCfg.getNoResultText() : "（本轮无检索结果）";

        StringBuilder builder = new StringBuilder();
        if (context != null && !context.isBlank()) {
            builder.append("请根据下面的本轮参考信息回答问题。")
                    .append("如果参考信息中能直接或间接回答问题，请优先使用参考信息；")
                    .append("允许结合常识补充解释，但不要编造企业内部制度、数字或文件内容。")
                    .append("回答正文保持简洁，不要在正文输出“来源#编号”、文件名引用或 <<REF>>、<<END>> 标记；")
                    .append("来源会由系统在回答结束后单独折叠展示。\n\n")
                    .append(refStart).append("\n");
            builder.append(context);
            builder.append(refEnd)
                    .append("\n\n用户问题：\n")
                    .append(userMessage);
        } else {
            builder.append("当前没有检索到可用的知识库参考信息。")
                    .append("如果用户在问身份、寒暄、通用概念、写作、翻译、使用帮助等常规问题，")
                    .append("可以直接回答常规问题，不要机械回答“暂无相关信息”。")
                    .append("如果用户明确询问企业制度、流程、文档数据、统计数字或内部事实，")
                    .append("请说明当前知识库没有找到相关依据，并建议用户换个问法或补充资料。")
                    .append("\n\n")
                    .append(refStart).append("\n")
                    .append(noResult).append("\n")
                    .append(refEnd)
                    .append("\n\n用户问题：\n")
                    .append(userMessage);
        }
        return builder.toString();
    }

    private List<Map<String, String>> sanitizeHistory(List<Map<String, String>> history) {
        List<Map<String, String>> sanitized = new ArrayList<>();
        if (history == null || history.isEmpty()) {
            return sanitized;
        }

        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            Map<String, String> message = history.get(i);
            if (message == null) {
                continue;
            }

            String role = message.get("role");
            String content = message.get("content");
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            if (content == null || content.isBlank()) {
                continue;
            }

            String cleanContent = stripThinking(content).trim();
            if (cleanContent.isBlank() || cleanContent.contains("回答已被用户停止")) {
                continue;
            }
            if ("assistant".equals(role) && isNoResultAnswer(cleanContent)) {
                continue;
            }

            sanitized.add(Map.of("role", role, "content", cleanContent));
        }
        return sanitized;
    }

    private String stripThinking(String content) {
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private boolean isNoResultAnswer(String content) {
        String normalized = content
                .replace("。", "")
                .replace(".", "")
                .replaceAll("\\s+", "");
        return normalized.equals("暂无相关信息") || normalized.equals("暂时没有相关信息");
    }

    private static final class ThinkingTagFilter {
        private static final String THINK_START = "<think>";
        private static final String THINK_END = "</think>";

        private boolean inThinking;
        private String pending = "";

        private String filter(String chunk) {
            String text = pending + chunk;
            pending = "";
            StringBuilder visible = new StringBuilder();
            int index = 0;

            while (index < text.length()) {
                if (inThinking) {
                    int end = text.indexOf(THINK_END, index);
                    if (end < 0) {
                        pending = keepPossibleTagSuffix(text, THINK_END);
                        return visible.toString();
                    }
                    index = end + THINK_END.length();
                    inThinking = false;
                    continue;
                }

                int start = text.indexOf(THINK_START, index);
                if (start < 0) {
                    int keep = possiblePrefixSuffixLength(text.substring(index), THINK_START);
                    int emitEnd = text.length() - keep;
                    visible.append(text, index, emitEnd);
                    pending = text.substring(emitEnd);
                    return visible.toString();
                }

                visible.append(text, index, start);
                index = start + THINK_START.length();
                inThinking = true;
            }

            return visible.toString();
        }

        private static String keepPossibleTagSuffix(String text, String tag) {
            int keep = Math.min(tag.length() - 1, text.length());
            return text.substring(text.length() - keep);
        }

        private static int possiblePrefixSuffixLength(String text, String tag) {
            int max = Math.min(tag.length() - 1, text.length());
            for (int len = max; len > 0; len--) {
                if (tag.startsWith(text.substring(text.length() - len))) {
                    return len;
                }
            }
            return 0;
        }
    }
    
    private void processChunk(String chunk, Consumer<String> onChunk) {
        try {
            if (chunk == null || chunk.isBlank()) {
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            for (String line : chunk.split("\\R")) {
                String payload = line.trim();
                if (payload.isEmpty() || payload.startsWith(":")) {
                    continue;
                }
                if (payload.startsWith("data:")) {
                    payload = payload.substring("data:".length()).trim();
                }

                if ("[DONE]".equals(payload)) {
                    logger.debug("对话结束");
                    continue;
                }

                JsonNode node = mapper.readTree(payload);
                JsonNode delta = node.path("choices").path(0).path("delta");
                String content = delta.path("content").asText("");

                if (!content.isEmpty()) {
                    onChunk.accept(content);
                }
            }
        } catch (Exception e) {
            logger.error("处理数据块时出错: {}", e.getMessage(), e);
        }
    }
} 
