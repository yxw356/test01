package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.KnowledgeCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeCategoryRepository extends JpaRepository<KnowledgeCategory, Long> {
    List<KnowledgeCategory> findByEnabledTrueOrderBySortOrderAscNameAsc();

    Optional<KnowledgeCategory> findByIdAndEnabledTrue(Long id);
}
