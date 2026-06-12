package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.KnowledgeFaq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeFaqRepository extends JpaRepository<KnowledgeFaq, Long> {
    List<KnowledgeFaq> findAllByOrderByUpdatedAtDesc();

    List<KnowledgeFaq> findByEnabledTrueOrderByUpdatedAtDesc();
}
