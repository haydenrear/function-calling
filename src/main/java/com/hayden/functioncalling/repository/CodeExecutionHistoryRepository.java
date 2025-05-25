package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeExecutionHistoryRepository extends JpaRepository<CodeExecutionHistory, Long>, QuerydslPredicateExecutor<CodeExecutionHistory> {
    
    Optional<CodeExecutionHistory> findByExecutionId(String executionId);
    
    List<CodeExecutionHistory> findByRegistrationId(String registrationId);
    
    List<CodeExecutionHistory> findTop10ByOrderByCreatedTimeDesc();
}