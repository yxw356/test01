package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.RoleFilePermission;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.RoleFilePermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RoleFilePermissionService {

    private final RoleFilePermissionRepository repository;

    public RoleFilePermissionService(RoleFilePermissionRepository repository) {
        this.repository = repository;
    }

    public boolean isAllowed(User.Role role, FilePermissionAction action, boolean defaultAllowed) {
        if (role == null || action == null) {
            return defaultAllowed;
        }
        Map<FilePermissionAction, Boolean> configured = loadRolePermissionMap(role);
        return configured.getOrDefault(action, defaultAllowed);
    }

    public List<RoleFilePermission> listRolePermissions(User.Role role) {
        return repository.findByRole(role);
    }

    public List<PermissionView> listEffectivePermissions(User.Role role) {
        Map<FilePermissionAction, Boolean> configured = loadRolePermissionMap(role);
        return java.util.Arrays.stream(FilePermissionAction.values())
                .map(action -> new PermissionView(
                        action,
                        configured.getOrDefault(action, defaultPermission(role, action)),
                        configured.containsKey(action)
                ))
                .toList();
    }

    @Transactional
    public void updateRolePermissions(User.Role role, List<PermissionUpdate> updates) {
        for (PermissionUpdate update : updates) {
            RoleFilePermission permission = repository.findByRoleAndAction(role, update.action())
                    .orElseGet(() -> {
                        RoleFilePermission created = new RoleFilePermission();
                        created.setRole(role);
                        created.setAction(update.action());
                        return created;
                    });
            permission.setAllowed(update.allowed());
            repository.save(permission);
        }
    }

    private Map<FilePermissionAction, Boolean> loadRolePermissionMap(User.Role role) {
        Map<FilePermissionAction, Boolean> result = new EnumMap<>(FilePermissionAction.class);
        repository.findByRole(role).forEach(permission -> result.put(permission.getAction(), permission.isAllowed()));
        return result;
    }

    private boolean defaultPermission(User.Role role, FilePermissionAction action) {
        if (role == null || action == null) {
            return false;
        }
        if (role == User.Role.SUPER_ADMIN || role == User.Role.ADMIN) {
            return true;
        }
        return switch (action) {
            case VIEW, PREVIEW, DOWNLOAD -> true;
            case UPLOAD_PUBLIC -> role == User.Role.KNOWLEDGE_ADMIN;
            case UPLOAD_DEPARTMENT -> role == User.Role.DEPT_LEAD;
            case UPLOAD_PRIVATE -> role != User.Role.ADMIN;
            case DELETE, RECLEAN, REINDEX -> role == User.Role.DEPT_LEAD || role == User.Role.KNOWLEDGE_ADMIN;
            case RESUME_UPLOAD -> true;
        };
    }

    public record PermissionUpdate(FilePermissionAction action, boolean allowed) {}

    public record PermissionView(FilePermissionAction action, boolean allowed, boolean configured) {}
}
