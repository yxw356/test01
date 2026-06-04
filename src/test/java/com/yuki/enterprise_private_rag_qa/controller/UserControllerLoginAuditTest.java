package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.model.AuditAction;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.service.AuditService;
import com.yuki.enterprise_private_rag_qa.service.UserService;
import com.yuki.enterprise_private_rag_qa.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerLoginAuditTest {

    private UserController controller;
    private UserService userService;
    private JwtUtils jwtUtils;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        controller = new UserController();
        userService = mock(UserService.class);
        jwtUtils = mock(JwtUtils.class);
        auditService = mock(AuditService.class);

        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(controller, "userRepository", mock(UserRepository.class));
        ReflectionTestUtils.setField(controller, "auditService", auditService);
    }

    @Test
    void loginAuditIncludesIpAndDeviceInformation() {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", userAgent);
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        when(userService.authenticateUser("admin", "admin123")).thenReturn("admin");
        when(jwtUtils.generateToken("admin")).thenReturn("token");
        when(jwtUtils.generateRefreshToken("admin")).thenReturn("refresh");

        controller.login(new UserRequest("admin", "admin123"), request);

        verify(auditService).recordSuccess(
                eq(null),
                eq("admin"),
                eq(AuditAction.LOGIN),
                eq("user"),
                eq("admin"),
                eq("login success"),
                eq("203.0.113.10"),
                anyLong(),
                eq(userAgent),
                eq("Desktop"),
                eq("Chrome"),
                eq("Windows")
        );
    }
}
