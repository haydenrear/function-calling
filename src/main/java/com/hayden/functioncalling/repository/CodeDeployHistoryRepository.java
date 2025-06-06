package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeDeployHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeDeployHistoryRepository extends JpaRepository<CodeDeployHistory, Long> {

    Optional<CodeDeployHistory> findByDeployId(String deployId);

    List<CodeDeployHistory> findTop10ByOrderByCreatedTimeDesc();

    List<CodeDeployHistory> findByRegistrationIdOrderByCreatedTimeDesc(String registrationId);

    List<CodeDeployHistory> findBySessionIdOrderByCreatedTimeDesc(String sessionId);

    List<CodeDeployHistory> findByIsRunningTrue();
}
