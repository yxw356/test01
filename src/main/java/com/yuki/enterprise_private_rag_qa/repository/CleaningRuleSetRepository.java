package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.CleaningRuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CleaningRuleSetRepository extends JpaRepository<CleaningRuleSet, Long> {
    List<CleaningRuleSet> findByEnabledTrueOrderByUpdatedAtDesc();

    Optional<CleaningRuleSet> findByIdAndEnabledTrue(Long id);
}
