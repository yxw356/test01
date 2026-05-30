package com.yuki.enterprise_private_rag_qa.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class MonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringService.class);

    private final ElasticsearchClient esClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final OperationMetricsService metricsService;
    private final WebClient webClient;

    @Value("${spring.kafka.bootstrap-servers:127.0.0.1:9092}")
    private String kafkaBootstrapServers;

    @Value("${spring.kafka.consumer.group-id:file-processing-group}")
    private String kafkaConsumerGroup;

    @Value("${spring.kafka.topic.file-processing:file-processing}")
    private String fileProcessingTopic;

    @Value("${deepseek.api.url:http://127.0.0.1:8000/v1}")
    private String llmApiUrl;

    @Value("${deepseek.api.key:}")
    private String llmApiKey;

    @Value("${embedding.api.url:http://127.0.0.1:8001/v1}")
    private String embeddingApiUrl;

    @Value("${embedding.api.key:}")
    private String embeddingApiKey;

    public MonitoringService(ElasticsearchClient esClient,
                             RedisTemplate<String, String> redisTemplate,
                             OperationMetricsService metricsService,
                             WebClient.Builder webClientBuilder) {
        this.esClient = esClient;
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
        this.webClient = webClientBuilder.build();
    }

    public Map<String, Object> collectStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("timestamp", java.time.Instant.now().toString());

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("redis", checkRedis());
        components.put("elasticsearch", checkElasticsearch());
        components.put("vllmChat", checkHttpEndpoint(llmApiUrl + "/models", llmApiKey));
        components.put("vllmEmbedding", checkHttpEndpoint(embeddingApiUrl + "/models", embeddingApiKey));
        components.put("kafka", checkKafkaLag());
        status.put("components", components);

        Instant lastFailureAt = metricsService.getLastIndexFailureAt();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("indexSuccessCount", metricsService.getIndexSuccessCount());
        metrics.put("indexFailureCount", metricsService.getIndexFailureCount());
        metrics.put("lastIndexFailureMessage", nullToEmpty(metricsService.getLastIndexFailureMessage()));
        metrics.put("lastIndexFailureAt", lastFailureAt != null ? lastFailureAt.toString() : "");
        metrics.put("chatRequestCount", metricsService.getChatRequestCount());
        metrics.put("chatAverageDurationMs", metricsService.getChatAverageDurationMs());
        metrics.put("chatP95EstimateMs", metricsService.getChatP95EstimateMs());
        status.put("metrics", metrics);

        return status;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> result = new HashMap<>();
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            result.put("status", "UP");
            result.put("detail", pong);
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("detail", nullToEmpty(e.getMessage()));
        }
        return result;
    }

    private Map<String, Object> checkElasticsearch() {
        Map<String, Object> result = new HashMap<>();
        try {
            HealthResponse health = esClient.cluster().health();
            long count = esClient.count(c -> c.index("knowledge_base")).count();
            result.put("status", health.status() == HealthStatus.Red ? "DOWN" : "UP");
            result.put("clusterStatus", health.status().jsonValue());
            result.put("knowledgeBaseCount", count);
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("detail", nullToEmpty(e.getMessage()));
        }
        return result;
    }

    private Map<String, Object> checkHttpEndpoint(String url, String apiKey) {
        Map<String, Object> result = new HashMap<>();
        try {
            WebClient.RequestHeadersSpec<?> spec = webClient.get().uri(url);
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header("Authorization", "Bearer " + apiKey);
            }
            String body = spec.retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
            result.put("status", "UP");
            result.put("detail", body != null && body.length() > 120 ? body.substring(0, 120) + "..." : nullToEmpty(body));
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("detail", nullToEmpty(e.getMessage()));
        }
        return result;
    }

    private Map<String, Object> checkKafkaLag() {
        Map<String, Object> result = new HashMap<>();
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaBootstrapServers,
                "request.timeout.ms", "5000"))) {
            ListConsumerGroupOffsetsResult offsetsResult =
                    admin.listConsumerGroupOffsets(kafkaConsumerGroup);
            Map<TopicPartition, OffsetAndMetadata> committed =
                    offsetsResult.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

            long totalLag = 0;
            Map<String, Long> partitionLag = new LinkedHashMap<>();

            for (TopicPartition tp : committed.keySet()) {
                if (!fileProcessingTopic.equals(tp.topic())) {
                    continue;
                }
                ListOffsetsResult.ListOffsetsResultInfo endInfo = admin.listOffsets(
                        Map.of(tp, OffsetSpec.latest())
                ).all().get(5, TimeUnit.SECONDS).get(tp);
                long endOffset = endInfo.offset();
                long committedOffset = committed.get(tp).offset();
                long lag = Math.max(endOffset - committedOffset, 0);
                partitionLag.put(tp.partition() + "", lag);
                totalLag += lag;
            }

            result.put("status", totalLag > 100 ? "DEGRADED" : "UP");
            result.put("consumerGroup", kafkaConsumerGroup);
            result.put("topic", fileProcessingTopic);
            result.put("totalLag", totalLag);
            result.put("partitionLag", partitionLag);
        } catch (Exception e) {
            logger.debug("Kafka lag 检查失败: {}", e.getMessage());
            result.put("status", "UNKNOWN");
            result.put("detail", nullToEmpty(e.getMessage()));
        }
        return result;
    }
}
