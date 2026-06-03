package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.*;
import lombok.Data;

import java.sql.Blob;

/**
 * 文档向量实体类
 * 用于存储文本分块和相关元数据
 */
@Data
@Entity
@Table(name = "document_vectors")
public class DocumentVector {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vectorId;

    @Column(nullable = false, length = 32)
    private String fileMd5;

    @Column(nullable = false)
    private Integer chunkId;

    /**
     * 父块ID，同一父块下可能包含多个子块。
     */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    @Lob
    @Column(name = "text_content", columnDefinition = "LONGTEXT")
    private String textContent;

    /**
     * 父块完整文本，用于检索命中子块后的上下文回溯。
     */
    @Lob
    @Column(name = "parent_text_content", columnDefinition = "LONGTEXT")
    private String parentTextContent;

    @Column(length = 32)
    private String modelVersion;
    
    /**
     * 上传用户ID
     */
    @Column(nullable = false, name = "user_id", length = 64)
    private String userId;
    
    /**
     * 文件所属组织标签
     */
    @Column(name = "org_tag", length = 50)
    private String orgTag;

    /**
     * 知识范围：PUBLIC公共知识、DEPARTMENT部门知识、PRIVATE个人知识。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_scope", nullable = false)
    private FileUpload.KnowledgeScope knowledgeScope = FileUpload.KnowledgeScope.DEPARTMENT;

    /**
     * 部门专有知识所属部门。第一阶段复用组织标签ID。
     */
    @Column(name = "department_id", length = 50)
    private String departmentId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "category_name", length = 120)
    private String categoryName;
    
    /**
     * 文件是否公开
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;
}
