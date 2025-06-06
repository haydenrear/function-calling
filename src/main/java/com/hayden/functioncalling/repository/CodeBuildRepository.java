package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeBuildEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeBuildRepository extends JpaRepository<CodeBuildEntity, Long> {

    Optional<CodeBuildEntity> findByRegistrationId(String registrationId);

    List<CodeBuildEntity> findByEnabledTrue();

    boolean existsByRegistrationId(String registrationId);
}
