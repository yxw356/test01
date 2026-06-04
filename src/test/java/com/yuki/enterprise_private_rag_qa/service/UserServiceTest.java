package com.yuki.enterprise_private_rag_qa.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import com.yuki.enterprise_private_rag_qa.exception.CustomException;
import com.yuki.enterprise_private_rag_qa.model.OrganizationTag;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.utils.PasswordUtil;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/**
 * UserService 的测试类
 */
class UserServiceTest {
    // 模拟 UserRepository 实例
    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationTagRepository organizationTagRepository;

    @Mock
    private OrgTagCacheService orgTagCacheService;

    // 注入模拟的 UserService 实例
    @InjectMocks
    private UserService userService;

    /**
     * 在每个测试方法执行前初始化模拟对象
     */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * 测试用户注册成功的情况
     */
    @Test
    void testRegisterUser_Success() {
        // 假设用户名 "testuser" 在数据库中不存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId(anyString())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 调用 userService 的 registerUser 方法进行用户注册
        userService.registerUser("testuser", "password123");

        // 创建 ArgumentCaptor 来捕获 save 方法的参数
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // 验证 userRepository.save 被调用，并捕获参数
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());

        // 获取捕获的 User 对象并进行断言
        User savedUser = userCaptor.getValue();
        assertNotNull(savedUser);
        assertEquals("testuser", savedUser.getUsername());
    }

    /**
     * 测试用户注册时用户名已存在的情况
     */
    @Test
    void testRegisterUser_UsernameExists() {
        // 假设用户名 "testuser" 在数据库中已存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(new User()));

        // 断言在注册已存在的用户名时抛出 CustomException 异常
        CustomException exception = assertThrows(CustomException.class, () -> userService.registerUser("testuser", "password123"));
        assertEquals("Username already exists", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    /**
     * 测试用户认证成功的情况
     */
   @Test
void testAuthenticateUser_Success() {
    // 使用 PasswordUtil 生成加密密码
    String rawPassword = "password123";
    String encodedPassword = PasswordUtil.encode(rawPassword);

    // 创建一个带有加密密码的用户对象，并确保设置了用户名
    User user = new User();
    user.setUsername("testuser"); // 确保设置了用户名
    user.setPassword(encodedPassword);

    when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

    try {
        // 调用 userService 的 authenticateUser 方法进行用户认证
        String username = userService.authenticateUser("testuser", rawPassword);

        // 打印返回的用户名（仅用于调试）
        System.out.println("Returned username: " + username);

        // 断言返回的用户名是否正确
        assertEquals("testuser", username);
    } catch (CustomException e) {
        // 捕获并打印异常信息
        System.out.println("Exception message: " + e.getMessage());
        throw e;
    }

    // 打印实际的加密密码（仅用于调试）
    System.out.println("Actual encrypted password: " + user.getPassword());
}





    /**
     * 测试用户认证失败的情况
     */
    @Test
    void testAuthenticateUser_InvalidCredentials() {
        // 假设用户名 "testuser" 在数据库中不存在
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // 断言在使用错误密码认证时抛出 CustomException 异常
        CustomException exception = assertThrows(CustomException.class, () -> userService.authenticateUser("testuser", "wrongpassword"));
        assertEquals("Invalid username or password", exception.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void changeOwnPasswordRequiresCurrentPassword() {
        User user = new User();
        user.setUsername("member");
        user.setPassword(PasswordUtil.encode("oldPass123"));
        when(userRepository.findByUsername("member")).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
                () -> userService.changeOwnPassword("member", "bad", "newPass123"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeOwnPasswordUpdatesEncodedPassword() {
        User user = new User();
        user.setUsername("member");
        user.setPassword(PasswordUtil.encode("oldPass123"));
        when(userRepository.findByUsername("member")).thenReturn(Optional.of(user));

        userService.changeOwnPassword("member", "oldPass123", "newPass123");

        verify(userRepository).save(user);
        assertTrue(PasswordUtil.matches("newPass123", user.getPassword()));
    }

    @Test
    void superAdminCanCreateManagedUserWithAnyRole() {
        User creator = user("admin", User.Role.SUPER_ADMIN, "HR");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(creator));
        when(userRepository.findByUsername("newLead")).thenReturn(Optional.empty());
        when(organizationTagRepository.existsByTagId("HR")).thenReturn(true);
        when(organizationTagRepository.existsByTagId(startsWith("PRIVATE_"))).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.createManagedUser("admin", new UserService.ManagedUserRequest(
                "newLead", "pass12345", User.Role.DEPT_LEAD, List.of("HR"), "HR"
        ));

        assertEquals(User.Role.DEPT_LEAD, created.getRole());
        assertTrue(created.getOrgTags().contains("HR"));
        assertEquals("HR", created.getPrimaryOrg());
    }

    @Test
    void departmentLeadCanOnlyCreateDepartmentMemberInsideOwnDepartment() {
        User creator = user("lead", User.Role.DEPT_LEAD, "HR");
        when(userRepository.findByUsername("lead")).thenReturn(Optional.of(creator));

        CustomException roleException = assertThrows(CustomException.class,
                () -> userService.createManagedUser("lead", new UserService.ManagedUserRequest(
                        "badLead", "pass12345", User.Role.DEPT_LEAD, List.of("HR"), "HR"
                )));
        assertEquals(HttpStatus.FORBIDDEN, roleException.getStatus());

        CustomException deptException = assertThrows(CustomException.class,
                () -> userService.createManagedUser("lead", new UserService.ManagedUserRequest(
                        "outsider", "pass12345", User.Role.DEPT_MEMBER, List.of("FIN"), "FIN"
                )));
        assertEquals(HttpStatus.FORBIDDEN, deptException.getStatus());
    }

    @Test
    void departmentLeadCanCreateDepartmentMember() {
        User creator = user("lead", User.Role.DEPT_LEAD, "HR");
        when(userRepository.findByUsername("lead")).thenReturn(Optional.of(creator));
        when(userRepository.findByUsername("newMember")).thenReturn(Optional.empty());
        when(orgTagCacheService.getUserEffectiveOrgTags("lead")).thenReturn(List.of("HR"));
        when(organizationTagRepository.existsByTagId("HR")).thenReturn(true);
        when(organizationTagRepository.existsByTagId(startsWith("PRIVATE_"))).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.createManagedUser("lead", new UserService.ManagedUserRequest(
                "newMember", "pass12345", User.Role.DEPT_MEMBER, List.of("HR"), "HR"
        ));

        assertEquals(User.Role.DEPT_MEMBER, created.getRole());
        assertTrue(created.getOrgTags().contains("HR"));
        assertEquals("HR", created.getPrimaryOrg());
    }

    private User user(String username, User.Role role, String orgTags) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        user.setOrgTags(orgTags);
        user.setPrimaryOrg(orgTags == null || orgTags.isBlank() ? null : orgTags.split(",")[0]);
        return user;
    }
}
