package com.yuki.enterprise_private_rag_qa.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.service.FilePermissionAction;
import com.yuki.enterprise_private_rag_qa.service.RoleFilePermissionService;
import com.yuki.enterprise_private_rag_qa.service.UserService;
import com.yuki.enterprise_private_rag_qa.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    private AdminController controller;
    private UserRepository userRepository;
    private JwtUtils jwtUtils;
    private RoleFilePermissionService roleFilePermissionService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        controller = new AdminController();
        userRepository = mock(UserRepository.class);
        jwtUtils = mock(JwtUtils.class);
        roleFilePermissionService = mock(RoleFilePermissionService.class);
        userService = mock(UserService.class);

        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "organizationTagRepository", mock(OrganizationTagRepository.class));
        ReflectionTestUtils.setField(controller, "redisTemplate", mock(RedisTemplate.class));
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(controller, "roleFilePermissionService", roleFilePermissionService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void superAdminCanUpdateManagedUser() {
        when(jwtUtils.extractUsernameFromToken("token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", User.Role.SUPER_ADMIN)));
        User updated = user(2L, "hr2", User.Role.DEPT_LEAD);
        updated.setOrgTags("PRIVATE_hr2,HR");
        updated.setPrimaryOrg("HR");
        when(userService.updateManagedUser("admin", 2L, new UserService.ManagedUserUpdateRequest(
                "hr2", User.Role.DEPT_LEAD, List.of("HR"), "HR"
        ))).thenReturn(updated);

        ResponseEntity<?> response = controller.updateManagedUser(
                "Bearer token",
                2L,
                new ManagedUserUpdateRequest("hr2", User.Role.DEPT_LEAD, List.of("HR"), "HR")
        );

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(200, body.get("code"));
        verify(userService).updateManagedUser("admin", 2L, new UserService.ManagedUserUpdateRequest(
                "hr2", User.Role.DEPT_LEAD, List.of("HR"), "HR"
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void superAdminCanDeleteManagedUser() {
        when(jwtUtils.extractUsernameFromToken("token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", User.Role.SUPER_ADMIN)));

        ResponseEntity<?> response = controller.deleteManagedUser("Bearer token", 2L);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(200, body.get("code"));
        verify(userService).deleteManagedUser("admin", 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void superAdminCanUpdateRoleFilePermissions() {
        when(jwtUtils.extractUsernameFromToken("token")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(1L, "admin", User.Role.SUPER_ADMIN)));

        ResponseEntity<?> response = controller.updateRoleFilePermissions(
                "Bearer token",
                "DEPT_LEAD",
                new RoleFilePermissionUpdateRequest(List.of(
                        new RoleFilePermissionItem(FilePermissionAction.DELETE, false),
                        new RoleFilePermissionItem(FilePermissionAction.RECLEAN, true)
                ))
        );

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(200, body.get("code"));
        verify(roleFilePermissionService).updateRolePermissions(User.Role.DEPT_LEAD, List.of(
                new RoleFilePermissionService.PermissionUpdate(FilePermissionAction.DELETE, false),
                new RoleFilePermissionService.PermissionUpdate(FilePermissionAction.RECLEAN, true)
        ));
    }

    @Test
    void departmentLeadCannotUpdateRoleFilePermissions() {
        when(jwtUtils.extractUsernameFromToken("token")).thenReturn("lead");
        when(userRepository.findByUsername("lead")).thenReturn(Optional.of(user(2L, "lead", User.Role.DEPT_LEAD)));

        ResponseEntity<?> response = controller.updateRoleFilePermissions(
                "Bearer token",
                "DEPT_MEMBER",
                new RoleFilePermissionUpdateRequest(List.of(
                        new RoleFilePermissionItem(FilePermissionAction.DELETE, true)
                ))
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    private User user(Long id, String username, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
