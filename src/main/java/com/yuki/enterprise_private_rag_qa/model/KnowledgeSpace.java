package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_space", indexes = {
        @Index(name = "idx_knowledge_space_space_id", columnList = "space_id", unique = true),
        @Index(name = "idx_knowledge_space_department", columnList = "department_id")
})
public class KnowledgeSpace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "space_id", nullable = false, length = 80, unique = true)
    private String spaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 24)
    private SpaceType type;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "department_id", length = 80)
    private String departmentId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum SpaceType {
        PUBLIC,
        DEPARTMENT,
        PRIVATE
    }
}
