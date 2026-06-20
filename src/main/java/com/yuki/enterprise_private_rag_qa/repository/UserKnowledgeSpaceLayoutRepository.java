package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.UserKnowledgeSpaceLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserKnowledgeSpaceLayoutRepository extends JpaRepository<UserKnowledgeSpaceLayout, Long> {
    Optional<UserKnowledgeSpaceLayout> findByUserId(String userId);
}
