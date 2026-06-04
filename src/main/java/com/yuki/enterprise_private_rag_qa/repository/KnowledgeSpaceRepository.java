package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.KnowledgeSpace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgeSpaceRepository extends JpaRepository<KnowledgeSpace, Long> {
    boolean existsBySpaceId(String spaceId);

    Optional<KnowledgeSpace> findBySpaceId(String spaceId);
}
