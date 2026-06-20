package com.yuki.enterprise_private_rag_qa.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_knowledge_space_layout", indexes = {
        @Index(name = "idx_user_knowledge_space_layout_user", columnList = "user_id", unique = true)
})
public class UserKnowledgeSpaceLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64, unique = true)
    private String userId;

    @Column(name = "space_order", nullable = false, columnDefinition = "TEXT")
    private String spaceOrder = "[]";

    @Column(name = "collapsed_spaces", columnDefinition = "TEXT")
    private String collapsedSpaces = "[]";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
