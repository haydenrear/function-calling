package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeDeployEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeDeployRepository extends JpaRepository<CodeDeployEntity, Long> {

    Optional<CodeDeployEntity> findByRegistrationId(String registrationId);

    List<CodeDeployEntity> findByEnabledTrue();

    boolean existsByRegistrationId(String registrationId);
}
