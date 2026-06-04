package com.yuki.enterprise_private_rag_qa.model;

import com.yuki.enterprise_private_rag_qa.service.FilePermissionAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "role_file_permission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role", "action"})
)
public class RoleFilePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private User.Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FilePermissionAction action;

    @Column(nullable = false)
    private boolean allowed;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
