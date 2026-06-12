package com.yuki.enterprise_private_rag_qa.repository;

import com.yuki.enterprise_private_rag_qa.model.TrainingExamAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainingExamAttemptRepository extends JpaRepository<TrainingExamAttempt, Long> {

    List<TrainingExamAttempt> findByKnowledgeScopeAndDepartmentIdOrderByScoreDescDurationSecondsAscCreatedAtAsc(
            String knowledgeScope,
            String departmentId,
            Pageable pageable
    );

    List<TrainingExamAttempt> findByKnowledgeScopeOrderByScoreDescDurationSecondsAscCreatedAtAsc(
            String knowledgeScope,
            Pageable pageable
    );
}
