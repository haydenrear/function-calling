package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.TestExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestExecutionHistoryRepository extends JpaRepository<TestExecutionHistory, Long>, QuerydslPredicateExecutor<TestExecutionHistory> {
    
    Optional<TestExecutionHistory> findByExecutionId(String executionId);
    
    List<TestExecutionHistory> findByRegistrationId(String registrationId);
    
    List<TestExecutionHistory> findTop10ByOrderByCreatedTimeDesc();
}