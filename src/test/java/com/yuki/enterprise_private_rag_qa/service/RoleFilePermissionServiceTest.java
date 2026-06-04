package com.yuki.enterprise_private_rag_qa.service;

import com.yuki.enterprise_private_rag_qa.model.RoleFilePermission;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.RoleFilePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleFilePermissionServiceTest {

    private RoleFilePermissionRepository repository;
    private RoleFilePermissionService service;

    @BeforeEach
    void setUp() {
        repository = mock(RoleFilePermissionRepository.class);
        service = new RoleFilePermissionService(repository);
    }

    @Test
    void missingRoleConfigFallsBackToDefaultPermission() {
        when(repository.findByRole(User.Role.DEPT_MEMBER)).thenReturn(List.of());

        assertTrue(service.isAllowed(User.Role.DEPT_MEMBER, FilePermissionAction.VIEW, true));
        assertFalse(service.isAllowed(User.Role.DEPT_MEMBER, FilePermissionAction.DELETE, false));
    }

    @Test
    void explicitRoleConfigOverridesDefaultPermission() {
        RoleFilePermission permission = new RoleFilePermission();
        permission.setRole(User.Role.DEPT_MEMBER);
        permission.setAction(FilePermissionAction.DELETE);
        permission.setAllowed(true);
        when(repository.findByRole(User.Role.DEPT_MEMBER)).thenReturn(List.of(permission));

        assertTrue(service.isAllowed(User.Role.DEPT_MEMBER, FilePermissionAction.DELETE, false));
    }

    @Test
    void updateRolePermissionsUpsertsAllKnownActions() {
        RoleFilePermission existing = new RoleFilePermission();
        existing.setRole(User.Role.DEPT_LEAD);
        existing.setAction(FilePermissionAction.DELETE);
        existing.setAllowed(true);
        when(repository.findByRoleAndAction(User.Role.DEPT_LEAD, FilePermissionAction.DELETE))
                .thenReturn(Optional.of(existing));
        when(repository.findByRoleAndAction(User.Role.DEPT_LEAD, FilePermissionAction.RECLEAN))
                .thenReturn(Optional.empty());

        service.updateRolePermissions(User.Role.DEPT_LEAD, List.of(
                new RoleFilePermissionService.PermissionUpdate(FilePermissionAction.DELETE, false),
                new RoleFilePermissionService.PermissionUpdate(FilePermissionAction.RECLEAN, true)
        ));

        assertFalse(existing.isAllowed());
        verify(repository).save(existing);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.getRole() == User.Role.DEPT_LEAD
                        && saved.getAction() == FilePermissionAction.RECLEAN
                        && saved.isAllowed()
        ));
    }
}
