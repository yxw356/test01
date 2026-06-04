package com.yuki.enterprise_private_rag_qa.client;

import com.yuki.enterprise_private_rag_qa.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeepSeekClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void promptAllowsGeneralQuestionsWhenNoKnowledgeContextExists() {
        DeepSeekClient client = newClient(true);

        String content = invokeCurrentTurnContent(client, "你是谁", "");

        assertTrue(content.contains("可以直接回答常规问题"));
        assertTrue(content.contains("不要机械回答“暂无相关信息”"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void promptPrioritizesKnowledgeContextWhenContextExists() {
        DeepSeekClient client = newClient(true);

        String content = invokeCurrentTurnContent(client, "报销流程是什么", "[来源#1: handbook.md]\n报销需部门审批");

        assertTrue(content.contains("优先使用参考信息"));
        assertTrue(content.contains("[来源#1: handbook.md]"));
        assertTrue(content.contains("来源会由系统在回答结束后单独折叠展示"));
        assertFalse(content.contains("引用来源时"));
    }

    private DeepSeekClient newClient(boolean ragEnabled) {
        AiProperties properties = new AiProperties();
        AiProperties.Prompt prompt = new AiProperties.Prompt();
        prompt.setRules("测试规则");
        prompt.setRefStart("<<REF>>");
        prompt.setRefEnd("<<END>>");
        prompt.setNoResultText("（本轮无检索结果）");
        properties.setPrompt(prompt);
        properties.setGeneration(new AiProperties.Generation());
        return new DeepSeekClient("http://127.0.0.1:8000/v1", "key", "model", properties, ragEnabled);
    }

    private String invokeCurrentTurnContent(DeepSeekClient client, String userMessage, String context) {
        Map<String, Object> request = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                client,
                "buildRequest",
                userMessage,
                context,
                List.of()
        );
        List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
        return messages.get(messages.size() - 1).get("content");
    }
}
