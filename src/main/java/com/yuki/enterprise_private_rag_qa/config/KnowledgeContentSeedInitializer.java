package com.yuki.enterprise_private_rag_qa.config;

import com.yuki.enterprise_private_rag_qa.model.CleaningRuleSet;
import com.yuki.enterprise_private_rag_qa.model.FileUpload;
import com.yuki.enterprise_private_rag_qa.model.KnowledgeCategory;
import com.yuki.enterprise_private_rag_qa.repository.CleaningRuleSetRepository;
import com.yuki.enterprise_private_rag_qa.repository.KnowledgeCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(4)
public class KnowledgeContentSeedInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeContentSeedInitializer.class);

    private final KnowledgeCategoryRepository knowledgeCategoryRepository;
    private final CleaningRuleSetRepository cleaningRuleSetRepository;

    public KnowledgeContentSeedInitializer(KnowledgeCategoryRepository knowledgeCategoryRepository,
                                             CleaningRuleSetRepository cleaningRuleSetRepository) {
        this.knowledgeCategoryRepository = knowledgeCategoryRepository;
        this.cleaningRuleSetRepository = cleaningRuleSetRepository;
    }

    @Override
    public void run(String... args) {
        seedCategories();
        seedCleaningRules();
    }

    private void seedCategories() {
        if (knowledgeCategoryRepository.count() > 0) {
            return;
        }
        createCategory("制度与合规", FileUpload.KnowledgeScope.PUBLIC, null, 10);
        createCategory("流程与操作指南", FileUpload.KnowledgeScope.PUBLIC, null, 20);
        createCategory("产品与方案", FileUpload.KnowledgeScope.PUBLIC, null, 30);
        createCategory("培训与学习", FileUpload.KnowledgeScope.PUBLIC, null, 40);
        createCategory("项目资料", FileUpload.KnowledgeScope.DEPARTMENT, "default", 50);
        createCategory("FAQ / 常见问题", FileUpload.KnowledgeScope.PUBLIC, null, 60);
        logger.info("Seeded default knowledge categories");
    }

    private void seedCleaningRules() {
        if (cleaningRuleSetRepository.count() > 0) {
            return;
        }
        createRule("公共知识严格去噪", FileUpload.KnowledgeScope.PUBLIC, null,
                "^第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页$", true);
        createRule("部门知识保留结构", FileUpload.KnowledgeScope.DEPARTMENT, "default", null, false);
        createRule("个人知识轻量清洗", FileUpload.KnowledgeScope.PRIVATE, null, null, false);
        logger.info("Seeded default cleaning rule sets");
    }

    private void createCategory(String name, FileUpload.KnowledgeScope scope, String departmentId, int sortOrder) {
        KnowledgeCategory category = new KnowledgeCategory();
        category.setName(name);
        category.setKnowledgeScope(scope);
        category.setDepartmentId(departmentId);
        category.setSortOrder(sortOrder);
        category.setEnabled(true);
        category.setCreatedBy("system");
        knowledgeCategoryRepository.save(category);
    }

    private void createRule(String name, FileUpload.KnowledgeScope scope, String departmentId,
                            String dropPattern, boolean strict) {
        CleaningRuleSet ruleSet = new CleaningRuleSet();
        ruleSet.setName(name);
        ruleSet.setKnowledgeScope(scope);
        ruleSet.setDepartmentId(departmentId);
        ruleSet.setEnabled(true);
        ruleSet.setNormalizeLineBreaks(true);
        ruleSet.setNormalizeUnicodeSpaces(true);
        ruleSet.setNormalizeWhitespace(true);
        ruleSet.setTrimLines(true);
        ruleSet.setCollapseBlankLines(true);
        ruleSet.setRemoveDuplicateLines(strict);
        ruleSet.setMinDuplicateLineLength(8);
        if (dropPattern != null) {
            ruleSet.setDropLinePatterns("[\"" + dropPattern.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]");
        }
        cleaningRuleSetRepository.save(ruleSet);
    }
}
