package com.hayden.functioncalling.controller;

import com.hayden.commitdiffmodel.codegen.types.*;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.commitdiffmodel.convert.CommitDiffContextMapper;
import com.hayden.functioncalling.entity.CodeBuildEntity;
import com.hayden.functioncalling.entity.CodeBuildHistory;
import com.hayden.functioncalling.entity.CodeDeployEntity;
import com.hayden.functioncalling.entity.CodeDeployHistory;
import com.hayden.functioncalling.repository.CodeBuildHistoryRepository;
import com.hayden.functioncalling.repository.CodeBuildRepository;
import com.hayden.functioncalling.repository.CodeDeployHistoryRepository;
import com.hayden.functioncalling.repository.CodeDeployRepository;
import com.hayden.functioncalling.runner.BuildExecRunner;
import com.hayden.functioncalling.runner.DeployExecRunner;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class BuildDeployController {

    private final CodeBuildRepository buildRepository;
    private final CodeDeployRepository deployRepository;
    private final CodeBuildHistoryRepository buildHistoryRepository;
    private final CodeDeployHistoryRepository deployHistoryRepository;
    private final BuildExecRunner buildExecRunner;
    private final DeployExecRunner deployExecRunner;
    private final CommitDiffContextMapper mapper;

    // Build Queries
    @DgsQuery
    public List<CodeBuildRegistration> retrieveBuildRegistrations() {
        List<CodeBuildEntity> entities = buildRepository.findAll();
        return entities.stream()
                .map(this::mapToBuildRegistration)
                .collect(Collectors.toList());
    }

    @DgsQuery
    public List<CodeBuild> retrieveBuilds() {
        List<CodeBuildHistory> entities = buildHistoryRepository.findTop10ByOrderByCreatedTimeDesc();
        return entities.stream()
                .map(this::mapToBuild)
                .collect(Collectors.toList());
    }

    @DgsQuery
    public CodeBuildRegistration getCodeBuildRegistration(@InputArgument String registrationId) {
        Optional<CodeBuildEntity> entity = buildRepository.findByRegistrationId(registrationId);
        return entity.map(this::mapToBuildRegistration).orElse(null);
    }

    @DgsQuery
    public CodeBuildResult getBuildOutput(@InputArgument String buildId, @InputArgument String sessionId) {
        if (buildId == null || buildId.isBlank()) {
            return CodeBuildResult.newBuilder()
                    .success(false)
                    .sessionId(sessionId)
                    .error(List.of(new Error("Build ID is required")))
                    .build();
        }

        Optional<CodeBuildHistory> historyOpt = buildHistoryRepository.findByBuildId(buildId);

        if (historyOpt.isEmpty()) {
            return CodeBuildResult.newBuilder()
                    .sessionId(sessionId)
                    .success(false)
                    .error(List.of(new Error("No build found with ID: " + buildId)))
                    .build();
        }

        CodeBuildHistory history = historyOpt.get();

        return CodeBuildResult.newBuilder()
                .sessionId(sessionId)
                .success(history.getSuccess())
                .output(history.getOutput())
                .error(List.of(new Error(history.getError())))
                .exitCode(history.getExitCode())
                .buildId(buildId)
                .executionTime(history.getExecutionTimeMs())
                .artifactPaths(history.getArtifactPaths())
                .artifactOutputDirectory(history.getArtifactOutputDirectory())
                .buildLog(history.getBuildLog())
                .build();
    }

    // Deploy Queries
    @DgsQuery
    public List<CodeDeployRegistration> retrieveDeployRegistrations() {
        List<CodeDeployEntity> entities = deployRepository.findAll();
        return entities.stream()
                .map(this::mapToDeployRegistration)
                .collect(Collectors.toList());
    }

    @DgsQuery
    public List<CodeDeploy> retrieveDeploys() {
        List<CodeDeployHistory> entities = deployHistoryRepository.findTop10ByOrderByCreatedTimeDesc();
        return entities.stream()
                .map(this::mapToDeploy)
                .collect(Collectors.toList());
    }

    @DgsQuery
    public CodeDeployRegistration getCodeDeployRegistration(@InputArgument String registrationId) {
        Optional<CodeDeployEntity> entity = deployRepository.findByRegistrationId(registrationId);
        return entity.map(this::mapToDeployRegistration).orElse(null);
    }

    @DgsQuery
    public CodeDeployResult getDeployOutput(@InputArgument String deployId, @InputArgument String sessionId) {
        if (deployId == null || deployId.isBlank()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .sessionId(sessionId)
                    .error(List.of(new Error("Deploy ID is required")))
                    .build();
        }

        Optional<CodeDeployHistory> historyOpt = deployHistoryRepository.findByDeployId(deployId);

        if (historyOpt.isEmpty()) {
            return CodeDeployResult.newBuilder()
                    .sessionId(sessionId)
                    .success(false)
                    .error(List.of(new Error("No deployment found with ID: " + deployId)))
                    .build();
        }

        CodeDeployHistory history = historyOpt.get();

        return CodeDeployResult.newBuilder()
                .sessionId(sessionId)
                .success(history.getSuccess())
                .output(history.getOutput())
                .error(List.of(new Error(history.getError())))
                .exitCode(history.getExitCode())
                .deployId(deployId)
                .executionTime(history.getExecutionTimeMs())
                .deployLog(history.getDeployLog())
                .healthCheckStatus(history.getHealthCheckStatus())
                .healthCheckResponseTime(history.getHealthCheckResponseTimeMs())
                .isRunning(history.getIsRunning())
                .deploymentUrl(history.getDeploymentUrl())
                .build();
    }

    @DgsQuery
    public List<CodeDeploy> getRunningDeployments() {
        List<CodeDeployHistory> entities = deployHistoryRepository.findByIsRunningTrue();
        return entities.stream()
                .map(this::mapToDeploy)
                .collect(Collectors.toList());
    }

    // Build Mutations
    @DgsMutation
    
    public CodeBuildRegistration registerCodeBuild(@InputArgument CodeBuildRegistrationIn codeBuildRegistration) {
        CodeBuildEntity entity = mapper.map(codeBuildRegistration, CodeBuildEntity.class);

        entity = buildRepository.save(entity);
        log.info("Registered new code build: {}", entity.getRegistrationId());

        return mapToBuildRegistration(entity);
    }

    @DgsMutation
    
    public CodeBuildRegistration updateCodeBuildRegistration(
            @InputArgument String registrationId,
            @InputArgument Boolean enabled,
            @InputArgument String buildCommand,
            @InputArgument String workingDirectory,
            @InputArgument String arguments,
            @InputArgument Integer timeoutSeconds,
            @InputArgument String sessionId) {

        Optional<CodeBuildEntity> entityOpt = buildRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot update - no code build registration found with ID: {}", registrationId);
            return null;
        }

        CodeBuildEntity entity = entityOpt.get();
        entity.setSessionId(sessionId);

        if (enabled != null) {
            entity.setEnabled(enabled);
        }

        if (buildCommand != null) {
            entity.setBuildCommand(buildCommand);
        }

        if (workingDirectory != null) {
            entity.setWorkingDirectory(workingDirectory);
        }

        if (arguments != null) {
            entity.setArguments(arguments);
        }

        if (timeoutSeconds != null) {
            entity.setTimeoutSeconds(timeoutSeconds);
        }

        entity = buildRepository.save(entity);
        log.info("Updated code build registration: {}", entity.getRegistrationId());

        return mapToBuildRegistration(entity);
    }

    @DgsMutation
    
    public Boolean deleteCodeBuildRegistration(@InputArgument String registrationId, @InputArgument String sessionId) {
        Optional<CodeBuildEntity> entityOpt = buildRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot delete - no code build registration found with ID: {}", registrationId);
            return false;
        }

        buildRepository.delete(entityOpt.get());
        log.info("Deleted code build registration: {}", registrationId);

        return true;
    }

    @DgsMutation
    public CodeBuildResult build(@InputArgument CodeBuildOptions options) {
        log.info("Executing build with options: {}", options);

        if (options == null || options.getRegistrationId() == null) {
            return CodeBuildResult.newBuilder()
                    .success(false)
                    .sessionId(options != null ? options.getSessionId() : null)
                    .error(List.of(new Error("Invalid build options. Registration ID is required.")))
                    .build();
        }

        return buildExecRunner.build(options);
    }

    // Deploy Mutations
    @DgsMutation
    
    public CodeDeployRegistration registerCodeDeploy(@InputArgument CodeDeployRegistrationIn codeDeployRegistration) {
        CodeDeployEntity entity = mapper.map(codeDeployRegistration, CodeDeployEntity.class);

        entity = deployRepository.save(entity);
        log.info("Registered new code deploy: {}", entity.getRegistrationId());

        return mapToDeployRegistration(entity);
    }

    @DgsMutation
    
    public CodeDeployRegistration updateCodeDeployRegistration(
            @InputArgument String registrationId,
            @InputArgument Boolean enabled,
            @InputArgument String deployCommand,
            @InputArgument String workingDirectory,
            @InputArgument String arguments,
            @InputArgument Integer timeoutSeconds,
            @InputArgument String sessionId) {

        Optional<CodeDeployEntity> entityOpt = deployRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot update - no code deploy registration found with ID: {}", registrationId);
            return null;
        }

        CodeDeployEntity entity = entityOpt.get();
        entity.setSessionId(sessionId);

        if (enabled != null) {
            entity.setEnabled(enabled);
        }

        if (deployCommand != null) {
            entity.setDeployCommand(deployCommand);
        }

        if (workingDirectory != null) {
            entity.setWorkingDirectory(workingDirectory);
        }

        if (arguments != null) {
            entity.setArguments(arguments);
        }

        if (timeoutSeconds != null) {
            entity.setTimeoutSeconds(timeoutSeconds);
        }

        entity = deployRepository.save(entity);
        log.info("Updated code deploy registration: {}", entity.getRegistrationId());

        return mapToDeployRegistration(entity);
    }

    @DgsMutation
    public Boolean deleteCodeDeployRegistration(@InputArgument String registrationId, @InputArgument String sessionId) {
        Optional<CodeDeployEntity> entityOpt = deployRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot delete - no code deploy registration found with ID: {}", registrationId);
            return false;
        }

        deployRepository.delete(entityOpt.get());
        log.info("Deleted code deploy registration: {}", registrationId);

        return true;
    }

    @DgsMutation
    public CodeDeployResult deploy(@InputArgument CodeDeployOptions options) {
        log.info("Executing deploy with options: {}", options);

        if (options == null || options.getRegistrationId() == null) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .sessionId(options != null ? options.getSessionId() : null)
                    .error(List.of(new Error("Invalid deploy options. Registration ID is required.")))
                    .build();
        }

        return deployExecRunner.deploy(options);
    }

    @DgsMutation
    public CodeDeployResult stopDeployment(@InputArgument String registrationId, @InputArgument String sessionId) {
        log.info("Stopping deployment: {}", registrationId);

        if (registrationId == null || registrationId.isBlank()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .sessionId(sessionId)
                    .error(List.of(new Error("Registration ID is required")))
                    .build();
        }

        return deployExecRunner.stopDeployment(registrationId, sessionId);
    }

    // Mapping methods
    private CodeBuildRegistration mapToBuildRegistration(CodeBuildEntity entity) {
        return CodeBuildRegistration.newBuilder()
                .registrationId(entity.getRegistrationId())
                .buildCommand(entity.getBuildCommand())
                .workingDirectory(entity.getWorkingDirectory())
                .description(entity.getDescription())
                .arguments(entity.getArguments())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .enabled(entity.getEnabled())
                .artifactPaths(entity.getArtifactPaths())
                .artifactOutputDirectory(entity.getArtifactOutputDirectory())
                .build();
    }

    private CodeBuild mapToBuild(CodeBuildHistory entity) {
        return CodeBuild.newBuilder()
                .sessionId(entity.getSessionId())
                .registrationId(entity.getRegistrationId())
                .buildCommand(entity.getBuildCommand() + (entity.getArguments() != null ? " " + entity.getArguments() : ""))
                .status(entity.getSuccess() ? "SUCCESS" : "FAILED")
                .startTime(convertToDate(entity.getExecutionTimeMs()))
                .endTime(entity.getExecutionTimeMs() != null
                         ? LocalDate.ofInstant(Instant.ofEpochMilli(entity.getExecutionTimeMs())
                                 .plusMillis(entity.getExecutionTimeMs()), ZoneId.systemDefault())
                         : null)
                .exitCode(entity.getExitCode())
                .output(entity.getOutput())
                .error(List.of(new Error(entity.getError())))
                .buildId(entity.getBuildId())
                .artifactPaths(entity.getArtifactPaths())
                .build();
    }

    private CodeDeployRegistration mapToDeployRegistration(CodeDeployEntity entity) {
        return CodeDeployRegistration.newBuilder()
                .registrationId(entity.getRegistrationId())
                .deployCommand(entity.getDeployCommand())
                .workingDirectory(entity.getWorkingDirectory())
                .description(entity.getDescription())
                .arguments(entity.getArguments())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .enabled(entity.getEnabled())
                .healthCheckUrl(entity.getHealthCheckUrl())
                .stopCommand(entity.getStopCommand())
                .build();
    }

    private CodeDeploy mapToDeploy(CodeDeployHistory entity) {
        return CodeDeploy.newBuilder()
                .sessionId(entity.getSessionId())
                .registrationId(entity.getRegistrationId())
                .deployCommand(entity.getDeployCommand() + (entity.getArguments() != null ? " " + entity.getArguments() : ""))
                .status(entity.getSuccess() ? "SUCCESS" : "FAILED")
                .startTime(convertToDate(entity.getExecutionTimeMs()))
                .endTime(entity.getExecutionTimeMs() != null
                         ? LocalDate.ofInstant(Instant.ofEpochMilli(entity.getExecutionTimeMs())
                                 .plusMillis(entity.getExecutionTimeMs()), ZoneId.systemDefault())
                         : null)
                .exitCode(entity.getExitCode())
                .output(entity.getOutput())
                .error(List.of(new Error(entity.getError())))
                .deployId(entity.getDeployId())
                .healthCheckStatus(entity.getHealthCheckStatus())
                .isRunning(entity.getIsRunning())
                .deploymentUrl(entity.getDeploymentUrl())
                .build();
    }

    private LocalDate convertToDate(Integer localDateTime) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(localDateTime), ZoneId.systemDefault());
    }
}
