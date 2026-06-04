package com.yuki.enterprise_private_rag_qa.entity;

import lombok.Getter;
import lombok.Setter;

// 文件分块内容实体类
@Setter
@Getter
public class TextChunk {

    // Getters/Setters
    private int chunkId;       // 分块序号
    private String parentId;   // 父块ID
    private String content;    // 分块内容
    private String parentContent; // 父块完整内容
    private Long categoryId;
    private String categoryName;

    // 构造方法
    public TextChunk(int chunkId, String content) {
        this(chunkId, null, content, null);
    }

    public TextChunk(int chunkId, String parentId, String content, String parentContent) {
        this(chunkId, parentId, content, parentContent, null, null);
    }

    public TextChunk(int chunkId, String parentId, String content, String parentContent,
                     Long categoryId, String categoryName) {
        this.chunkId = chunkId;
        this.parentId = parentId;
        this.content = content;
        this.parentContent = parentContent;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }
}
