package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.KnowledgeCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeCaseRepository extends JpaRepository<KnowledgeCase, Long> {
    List<KnowledgeCase> findAllByOrderByUpdatedAtDesc();

    List<KnowledgeCase> findByEnabledTrueOrderByUpdatedAtDesc();
}
