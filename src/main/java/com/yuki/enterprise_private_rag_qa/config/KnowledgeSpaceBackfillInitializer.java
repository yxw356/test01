package com.yuki.enterprise_private_rag_qa.config;

import com.yuki.enterprise_private_rag_qa.service.KnowledgeSpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class KnowledgeSpaceBackfillInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeSpaceBackfillInitializer.class);

    private final KnowledgeSpaceService knowledgeSpaceService;

    public KnowledgeSpaceBackfillInitializer(KnowledgeSpaceService knowledgeSpaceService) {
        this.knowledgeSpaceService = knowledgeSpaceService;
    }

    @Override
    public void run(String... args) {
        try {
            KnowledgeSpaceService.BackfillResult result = knowledgeSpaceService.backfillSpacesAtStartup();
            logger.info("Knowledge space startup backfill finished: createdSpaces={}, linkedDocuments={}",
                    result.createdSpaces(), result.linkedDocuments());
        } catch (Exception e) {
            logger.warn("Knowledge space startup backfill failed: {}", e.getMessage(), e);
        }
    }
}
