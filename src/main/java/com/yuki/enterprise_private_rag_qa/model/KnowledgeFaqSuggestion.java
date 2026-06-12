package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_faq_suggestion")
public class KnowledgeFaqSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_question", nullable = false, length = 500, unique = true)
    private String normalizedQuestion;

    @Column(nullable = false, length = 500)
    private String question;

    @Column(name = "suggested_answer", columnDefinition = "TEXT")
    private String suggestedAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_scope", nullable = false)
    private FileUpload.KnowledgeScope knowledgeScope = FileUpload.KnowledgeScope.PUBLIC;

    @Column(name = "department_id", length = 80)
    private String departmentId;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount = 0;

    @Column(name = "hit_count", nullable = false)
    private int hitCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status = SuggestionStatus.PENDING;

    @Column(name = "last_asked_at")
    private LocalDateTime lastAskedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum SuggestionStatus {
        PENDING,
        ACCEPTED,
        IGNORED
    }
}
