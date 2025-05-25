package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeExecutionRepository extends JpaRepository<CodeExecutionEntity, Long> {
    
    Optional<CodeExecutionEntity> findByRegistrationId(String registrationId);
    
    List<CodeExecutionEntity> findByEnabledTrue();
    
    boolean existsByRegistrationId(String registrationId);
}