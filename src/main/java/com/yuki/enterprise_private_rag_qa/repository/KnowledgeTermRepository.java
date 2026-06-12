package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.KnowledgeTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeTermRepository extends JpaRepository<KnowledgeTerm, Long> {
    List<KnowledgeTerm> findAllByOrderByUpdatedAtDesc();

    List<KnowledgeTerm> findByEnabledTrueOrderByUpdatedAtDesc();
}
