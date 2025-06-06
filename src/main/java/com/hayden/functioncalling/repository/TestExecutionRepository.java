package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.TestExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestExecutionRepository extends JpaRepository<TestExecutionEntity, Long> {
    
    Optional<TestExecutionEntity> findByRegistrationId(String registrationId);
    
    List<TestExecutionEntity> findByEnabledTrue();
    
    boolean existsByRegistrationId(String registrationId);
}