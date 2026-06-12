package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "training_exam_attempt")
public class TrainingExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String userId;

    @Column(nullable = false, length = 120)
    private String username;

    @Column(name = "knowledge_scope", nullable = false, length = 20)
    private String knowledgeScope;

    @Column(name = "department_id", length = 80)
    private String departmentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private double score;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Lob
    @Column(name = "questions_json", columnDefinition = "TEXT")
    private String questionsJson;

    @Lob
    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson;

    @Lob
    @Column(name = "review_json", columnDefinition = "TEXT")
    private String reviewJson;

    @Lob
    @Column(name = "sources_json", columnDefinition = "TEXT")
    private String sourcesJson;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
