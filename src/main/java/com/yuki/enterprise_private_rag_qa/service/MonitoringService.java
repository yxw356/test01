package com.yuki.enterprise_private_rag_qa.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.IsolationLevel;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
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
    private final ChatConcurrencyLimiter chatConcurrencyLimiter;
    private final WebClient webClient;
    private final MinioClient minioClient;

    @Value("${spring.kafka.bootstrap-servers:127.0.0.1:9092}")
    private String kafkaBootstrapServers;

    @Value("${spring.kafka.consumer.group-id:file-processing-group}")
    private String kafkaConsumerGroup;

    @Value("${spring.kafka.topic.file-processing:file-processing}")
    private String fileProcessingTopic;

    @Value("${minio.bucketName:uploads}")
    private String minioBucketName;

    @Value("${minio.endpoint:http://localhost:19000}")
    private String minioEndpoint;

    @Value("${deepseek.api.url:http://127.0.0.1:8000/v1}")
    private String llmApiUrl;

    @Value("${deepseek.api.key:}")
    private String llmApiKey;

    @Value("${embedding.api.url:http://127.0.0.1:8001/v1}")
    private String embeddingApiUrl;

    @Value("${embedding.api.key:}")
    private String embeddingApiKey;

    @Value("${knowledge.upload.max-file-size:200MB}")
    private DataSize maxUploadFileSize;

    @Value("${knowledge.upload.redis.enabled:true}")
    private boolean uploadRedisEnabled;

    public MonitoringService(ElasticsearchClient esClient,
                             RedisTemplate<String, String> redisTemplate,
                             OperationMetricsService metricsService,
                             ChatConcurrencyLimiter chatConcurrencyLimiter,
                             MinioClient minioClient,
                             WebClient.Builder webClientBuilder) {
        this.esClient = esClient;
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
        this.chatConcurrencyLimiter = chatConcurrencyLimiter;
        this.minioClient = minioClient;
        this.webClient = webClientBuilder.build();
    }

    public Map<String, Object> collectStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("timestamp", java.time.Instant.now().toString());

        Map<String, Object> components = new LinkedHashMap<>();
        components.put("redis", checkRedis());
        components.put("minio", checkMinio());
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
        metrics.put("chatActiveCount", chatConcurrencyLimiter.getActiveCount());
        metrics.put("chatRejectedCount", chatConcurrencyLimiter.getRejectedCount());
        status.put("metrics", metrics);

        return status;
    }

    public Map<String, Object> collectUploadPreflightStatus() {
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("minio", checkMinio());
        components.put("redis", uploadRedisEnabled ? checkRedis() : skipped("local upload resume uses database fallback"));
        components.put("kafka", checkKafkaAvailability());

        List<String> unavailable = components.entrySet().stream()
                .filter(entry -> {
                    String status = String.valueOf(((Map<?, ?>) entry.getValue()).get("status"));
                    return !"UP".equals(status) && !"SKIPPED".equals(status);
                })
                .map(Map.Entry::getKey)
                .toList();

        boolean ready = unavailable.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ready", ready);
        result.put("components", components);
        result.put("uploadLimits", Map.of(
                "maxFileSize", maxUploadFileSize.toBytes(),
                "maxFileSizeLabel", formatDataSize(maxUploadFileSize.toBytes())
        ));
        result.put("message", ready
                ? "上传服务已就绪"
                : "上传依赖未启动：" + String.join("、", unavailable));
        return result;
    }

    private Map<String, Object> skipped(String detail) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "SKIPPED");
        result.put("detail", detail);
        return result;
    }

    private String formatDataSize(long bytes) {
        double mb = bytes / 1024.0 / 1024.0;
        if (mb >= 1) {
            return String.format("%.2fMB", mb);
        }
        return String.format("%.2fKB", bytes / 1024.0);
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

    private Map<String, Object> checkMinio() {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isTcpReachable(minioEndpoint, 1200)) {
                result.put("status", "DOWN");
                result.put("bucket", minioBucketName);
                result.put("detail", "MinIO endpoint unreachable: " + minioEndpoint);
                return result;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucketName).build());
            result.put("status", exists ? "UP" : "DOWN");
            result.put("bucket", minioBucketName);
            result.put("detail", exists ? "bucket exists" : "bucket missing");
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("bucket", minioBucketName);
            result.put("detail", nullToEmpty(e.getMessage()));
        }
        return result;
    }

    private boolean isTcpReachable(String endpoint, int timeoutMillis) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || port <= 0) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMillis);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
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
                "request.timeout.ms", "1500",
                "default.api.timeout.ms", "1500"))) {
            ListConsumerGroupOffsetsResult offsetsResult =
                    admin.listConsumerGroupOffsets(kafkaConsumerGroup);
            Map<TopicPartition, OffsetAndMetadata> committed =
                    offsetsResult.partitionsToOffsetAndMetadata().get(1500, TimeUnit.MILLISECONDS);

            long totalLag = 0;
            Map<String, Long> partitionLag = new LinkedHashMap<>();

            for (TopicPartition tp : committed.keySet()) {
                if (!fileProcessingTopic.equals(tp.topic())) {
                    continue;
                }
                ListOffsetsResult.ListOffsetsResultInfo endInfo = admin.listOffsets(
                        Map.of(tp, OffsetSpec.latest()),
                        new ListOffsetsOptions(IsolationLevel.READ_COMMITTED)
                ).all().get(1500, TimeUnit.MILLISECONDS).get(tp);
                long endOffset = endInfo.offset();
                long committedOffset = committed.get(tp).offset();
                long lag = Math.max(endOffset - committedOffset, 0);
                if (lag == 1) {
                    // 事务生产者会在日志尾部留下控制记录，监听器不会拿到它，监控里按无业务积压处理。
                    lag = 0;
                }
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

    private Map<String, Object> checkKafkaAvailability() {
        Map<String, Object> result = new HashMap<>();
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafkaBootstrapServers,
                "request.timeout.ms", "1500",
                "default.api.timeout.ms", "1500"))) {
            boolean topicExists = admin.listTopics().names().get(1500, TimeUnit.MILLISECONDS).contains(fileProcessingTopic);
            result.put("status", topicExists ? "UP" : "DOWN");
            result.put("topic", fileProcessingTopic);
            result.put("detail", topicExists ? "topic exists" : "topic missing");
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("topic", fileProcessingTopic);
            result.put("detail", nullToEmpty(e.getMessage()));
        }
        return result;
    }
}
