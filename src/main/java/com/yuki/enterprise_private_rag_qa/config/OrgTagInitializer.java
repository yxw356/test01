package com.yuki.enterprise_private_rag_qa.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.yuki.enterprise_private_rag_qa.model.OrganizationTag;
import com.yuki.enterprise_private_rag_qa.model.User;
import com.yuki.enterprise_private_rag_qa.repository.OrganizationTagRepository;
import com.yuki.enterprise_private_rag_qa.repository.UserRepository;

import java.util.Optional;

/**
 * 组织标签初始化器
 * 在应用启动时自动创建默认组织标签（如果不存在）
 */
@Component
@Order(2) // 设置优先级，确保在管理员账号初始化器之后运行
public class OrgTagInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(OrgTagInitializer.class);
    
    private static final String DEFAULT_TAG = "default";
    private static final String DEFAULT_NAME = "默认组织";
    private static final String DEFAULT_DESCRIPTION = "系统默认组织标签，自动分配给所有新用户";

    private static final String ADMIN_TAG = "admin";
    private static final String ADMIN_NAME = "管理员组织";
    private static final String ADMIN_DESCRIPTION = "管理员专用组织标签，具有管理权限";

    /** 业务知识库顶层标签，见 docs/知识库分域与边界定义.md */
    private static final String[][] KB_DOMAIN_TAGS = {
            {"KB_POLICY", "制度流程库", "请假、报销、采购、合同审批等制度与流程"},
            {"KB_PROJECT", "项目知识库", "项目资料、会议纪要、交付文档（子项目用下级标签）"},
            {"KB_PRESALES", "产品售前库", "方案、报价、案例、FAQ"},
            {"KB_OPS", "客服运维库", "故障处理、标准话术、操作手册"}
    };

    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Override
    public void run(String... args) throws Exception {
        // 查找管理员用户
        User adminUser = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new RuntimeException("管理员账号未找到，无法创建组织标签"));

        // 创建默认组织标签
        createOrganizationTagIfNotExists(DEFAULT_TAG, DEFAULT_NAME, DEFAULT_DESCRIPTION, adminUser);
        
        // 创建管理员组织标签
        createOrganizationTagIfNotExists(ADMIN_TAG, ADMIN_NAME, ADMIN_DESCRIPTION, adminUser);

        for (String[] kbTag : KB_DOMAIN_TAGS) {
            createOrganizationTagIfNotExists(kbTag[0], kbTag[1], kbTag[2], adminUser);
        }
        
        logger.info("组织标签初始化完成");
    }
    
    /**
     * 如果组织标签不存在，则创建
     */
    private void createOrganizationTagIfNotExists(String tagId, String name, String description, User creator) {
        logger.info("检查组织标签是否存在: {}", tagId);
        if (!organizationTagRepository.existsByTagId(tagId)) {
            logger.info("创建组织标签: {}", tagId);
            OrganizationTag tag = new OrganizationTag();
            tag.setTagId(tagId);
            tag.setName(name);
            tag.setDescription(description);
            tag.setCreatedBy(creator);
            organizationTagRepository.save(tag);
            logger.info("组织标签 '{}' 创建成功", tagId);
        } else {
            logger.info("组织标签 '{}' 已存在，跳过创建步骤", tagId);
        }
    }
} 