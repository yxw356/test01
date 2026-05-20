package com.yuki.enterprise_private_rag_qa.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Elasticsearch 检索结果映射（不含 vector，避免高维向量反序列化失败）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsSearchDocument {
    private String id;
    private String fileMd5;
    private Integer chunkId;
    private String parentId;
    private String textContent;
    private String parentTextContent;
    private String modelVersion;
    private String userId;
    private String orgTag;
    /** 使用包装类型，避免 Jackson 将 isPublic 映射为 JSON 字段 public */
    @JsonProperty("isPublic")
    private Boolean isPublic;
}
