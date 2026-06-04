package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cleaning_rule_set")
public class CleaningRuleSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_scope", nullable = false)
    private FileUpload.KnowledgeScope knowledgeScope = FileUpload.KnowledgeScope.DEPARTMENT;

    @Column(name = "department_id", length = 50)
    private String departmentId;

    @Column(length = 255)
    private String description;

    @Column(name = "normalize_line_breaks", nullable = false)
    private boolean normalizeLineBreaks = true;

    @Column(name = "normalize_unicode_spaces", nullable = false)
    private boolean normalizeUnicodeSpaces = true;

    @Column(name = "normalize_whitespace", nullable = false)
    private boolean normalizeWhitespace = true;

    @Column(name = "trim_lines", nullable = false)
    private boolean trimLines = true;

    @Column(name = "collapse_blank_lines", nullable = false)
    private boolean collapseBlankLines = true;

    @Column(name = "remove_duplicate_lines", nullable = false)
    private boolean removeDuplicateLines = true;

    @Column(name = "min_duplicate_line_length", nullable = false)
    private int minDuplicateLineLength = 8;

    @Lob
    @Column(name = "drop_line_patterns")
    private String dropLinePatterns;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
