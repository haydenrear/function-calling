package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeBuildHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeBuildHistoryRepository extends JpaRepository<CodeBuildHistory, Long> {

    Optional<CodeBuildHistory> findByBuildId(String buildId);

    List<CodeBuildHistory> findTop10ByOrderByCreatedTimeDesc();

    List<CodeBuildHistory> findByRegistrationIdOrderByCreatedTimeDesc(String registrationId);

    List<CodeBuildHistory> findBySessionIdOrderByCreatedTimeDesc(String sessionId);
}
