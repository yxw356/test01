package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.KnowledgeFaqSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeFaqSuggestionRepository extends JpaRepository<KnowledgeFaqSuggestion, Long> {
    Optional<KnowledgeFaqSuggestion> findByNormalizedQuestion(String normalizedQuestion);

    List<KnowledgeFaqSuggestion> findAllByOrderByHitCountDescUpdatedAtDesc();
}
