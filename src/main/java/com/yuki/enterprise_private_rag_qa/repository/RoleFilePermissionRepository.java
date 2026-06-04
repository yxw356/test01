package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.RoleFilePermission;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.service.FilePermissionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleFilePermissionRepository extends JpaRepository<RoleFilePermission, Long> {
    List<RoleFilePermission> findByRole(User.Role role);

    Optional<RoleFilePermission> findByRoleAndAction(User.Role role, FilePermissionAction action);
}
