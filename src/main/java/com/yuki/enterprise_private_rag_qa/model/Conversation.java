package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 对话记录唯一标识

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 关联用户

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question; // 用户提问内容

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer; // 系统回答内容

    /** Redis 会话 ID，便于关联多轮上下文 */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 结构化检索引用 JSON */
    @Column(name = "retrieval_citations", columnDefinition = "TEXT")
    private String retrievalCitations;

    @CreationTimestamp
    private LocalDateTime timestamp; // 对话时间戳
}