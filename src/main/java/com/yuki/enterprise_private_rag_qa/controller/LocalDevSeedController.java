package com.yuki.enterprise_private_rag_qa.controller;

import com.yuki.enterprise_private_rag_qa.model.FileIndexStatus;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.OrganizationTag;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.FileUploadRepository;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;
import com.yuki.enterprise_private_rag_qa.utils.PasswordUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Profile("local")
@RestController
@RequestMapping("/api/v1/admin/dev")
public class LocalDevSeedController {

    private static final String PASSWORD = "Test123456";
    private static final String DEPARTMENT_ID = "QA_DEPT";
    private static final String PUBLIC_MD5 = "11111111111111111111111111111111";
    private static final String DEPARTMENT_MD5 = "22222222222222222222222222222222";

    private final UserRepository userRepository;
    private final OrganizationTagRepository organizationTagRepository;
    private final FileUploadRepository fileUploadRepository;

    public LocalDevSeedController(UserRepository userRepository,
                                  OrganizationTagRepository organizationTagRepository,
                                  FileUploadRepository fileUploadRepository) {
        this.userRepository = userRepository;
        this.organizationTagRepository = organizationTagRepository;
        this.fileUploadRepository = fileUploadRepository;
    }

    @PostMapping("/knowledge-samples")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedKnowledgeSamples() {
        User admin = userRepository.findByUsername("admin")
                .orElseGet(() -> saveUser("admin", "admin123", User.Role.SUPER_ADMIN, "admin", "admin"));
        ensureDepartment(admin);

        User knowledgeAdmin = ensureUser("kb_admin_test", User.Role.KNOWLEDGE_ADMIN);
        User departmentLead = ensureUser("dept_lead_test", User.Role.DEPT_LEAD);
        ensureUser("dept_member_test", User.Role.DEPT_MEMBER);

        upsertDocument(PUBLIC_MD5, "本地验收-公共知识.md", knowledgeAdmin, FileUpload.KnowledgeScope.PUBLIC, null, true);
        upsertDocument(DEPARTMENT_MD5, "本地验收-部门知识.md", departmentLead, FileUpload.KnowledgeScope.DEPARTMENT, DEPARTMENT_ID, false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("departmentId", DEPARTMENT_ID);
        data.put("password", PASSWORD);
        data.put("users", Map.of(
                "knowledgeAdmin", "kb_admin_test",
                "departmentLead", "dept_lead_test",
                "departmentMember", "dept_member_test"
        ));
        data.put("documents", Map.of(
                "public", PUBLIC_MD5,
                "department", DEPARTMENT_MD5
        ));

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Local knowledge samples are ready",
                "data", data
        ));
    }

    private void ensureDepartment(User createdBy) {
        organizationTagRepository.findByTagId(DEPARTMENT_ID).orElseGet(() -> {
            OrganizationTag tag = new OrganizationTag();
            tag.setTagId(DEPARTMENT_ID);
            tag.setName("测试部门");
            tag.setDescription("本地权限验收部门");
            tag.setCreatedBy(createdBy);
            return organizationTagRepository.save(tag);
        });
    }

    private User ensureUser(String username, User.Role role) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    user.setRole(role);
                    user.setOrgTags(DEPARTMENT_ID);
                    user.setPrimaryOrg(DEPARTMENT_ID);
                    return userRepository.save(user);
                })
                .orElseGet(() -> saveUser(username, PASSWORD, role, DEPARTMENT_ID, DEPARTMENT_ID));
    }

    private User saveUser(String username, String password, User.Role role, String orgTags, String primaryOrg) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password));
        user.setRole(role);
        user.setOrgTags(orgTags);
        user.setPrimaryOrg(primaryOrg);
        return userRepository.save(user);
    }

    private void upsertDocument(String fileMd5, String fileName, User owner,
                                FileUpload.KnowledgeScope scope, String departmentId, boolean isPublic) {
        fileUploadRepository.findByFileMd5(fileMd5).ifPresent(fileUploadRepository::delete);

        FileUpload document = new FileUpload();
        document.setFileMd5(fileMd5);
        document.setFileName(fileName);
        document.setTotalSize(1024L);
        document.setStatus(1);
        document.setIndexStatus(FileIndexStatus.INDEXED);
        document.setUserId(String.valueOf(owner.getId()));
        document.setOrgTag(departmentId);
        document.setKnowledgeScope(scope);
        document.setDepartmentId(departmentId);
        document.setPublic(isPublic);
        fileUploadRepository.save(document);
    }
}
