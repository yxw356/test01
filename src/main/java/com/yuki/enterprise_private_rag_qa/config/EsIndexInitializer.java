package com.yuki.enterprise_private_rag_qa.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.yuki.enterprise_private_rag_qa.service.VectorizationService;
import org.apache.http.ConnectionClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Component
public class EsIndexInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EsIndexInitializer.class);
    private static final String INDEX_NAME = "knowledge_base";

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private VectorizationService vectorizationService;

    @Value("classpath:es-mappings/knowledge_base.json")
    private Resource mappingResource;

    @Value("${embedding.api.dimension:1024}")
    private int expectedVectorDims;

    @Override
    public void run(String... args) throws Exception {
        try {
            initializeIndex();
        } catch (Exception exception) {
            if (exception instanceof ConnectionClosedException
                    || (exception.getCause() != null && exception.getCause() instanceof ConnectionClosedException)) {
                logger.error("Elasticsearch连接已关闭，等待5秒后重试...");
                Thread.sleep(5000);
                initializeIndex();
            } else {
                throw new RuntimeException("初始化索引失败", exception);
            }
        }
    }

    private void initializeIndex() throws Exception {
        BooleanResponse existsResponse = esClient.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME)));
        if (!existsResponse.value()) {
            createIndex();
            reindexAfterMigration();
            return;
        }

        Integer currentDims = getCurrentVectorDims();
        if (currentDims == null || currentDims != expectedVectorDims) {
            logger.warn("索引 '{}' 向量维度 {} 与配置 embedding.api.dimension={} 不一致，将重建索引",
                    INDEX_NAME, currentDims, expectedVectorDims);
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(INDEX_NAME)));
            createIndex();
            reindexAfterMigration();
        } else {
            logger.info("索引 '{}' 已存在，向量维度={}", INDEX_NAME, currentDims);
        }
    }

    private void reindexAfterMigration() {
        try {
            int count = vectorizationService.reindexAllFromDatabase();
            logger.info("已从 MySQL 分块数据重建 Elasticsearch 索引，成功文件数: {}", count);
        } catch (Exception e) {
            logger.error("Elasticsearch 自动重建索引失败，请重启服务或重新上传文件", e);
        }
    }

    private Integer getCurrentVectorDims() {
        try {
            GetMappingResponse mapping = esClient.indices().getMapping(
                    GetMappingRequest.of(g -> g.index(INDEX_NAME)));
            Property vectorProperty = mapping.result()
                    .get(INDEX_NAME)
                    .mappings()
                    .properties()
                    .get("vector");
            if (vectorProperty == null || !vectorProperty.isDenseVector()) {
                return null;
            }
            DenseVectorProperty denseVector = vectorProperty.denseVector();
            return denseVector != null ? denseVector.dims() : null;
        } catch (Exception e) {
            logger.warn("读取索引映射失败: {}", e.getMessage());
            return null;
        }
    }

    private void createIndex() throws Exception {
        String mappingJson = new String(
                Files.readAllBytes(mappingResource.getFile().toPath()), StandardCharsets.UTF_8);

        CreateIndexRequest createIndexRequest = CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .withJson(new StringReader(mappingJson)));
        esClient.indices().create(createIndexRequest);
        logger.info("索引 '{}' 已创建，向量维度配置为 {}", INDEX_NAME, expectedVectorDims);
    }
}
