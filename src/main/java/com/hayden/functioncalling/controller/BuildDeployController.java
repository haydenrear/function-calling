package com.hayden.functioncalling.controller;

import com.google.common.collect.Lists;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import com.hayden.commitdiffcontext.convert.CommitDiffContextMapper;
import com.hayden.commitdiffmodel.codegen.types.*;
import com.hayden.commitdiffmodel.codegen.types.Error;
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
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

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
    @QueryMapping
    public List<CodeBuildRegistration> retrieveBuildRegistrations() {
        List<CodeBuildEntity> entities = buildRepository.findAll();
        return entities.stream()
                .map(this::mapToBuildRegistration)
                .collect(Collectors.toList());
    }

    @QueryMapping
    public List<CodeBuild> retrieveBuilds() {
        List<CodeBuildHistory> entities = buildHistoryRepository.findTop10ByOrderByCreatedTimeDesc();
        return entities.stream()
                .map(this::mapToBuild)
                .collect(Collectors.toList());
    }

    @QueryMapping
    public CodeBuildRegistration getCodeBuildRegistration(@Argument String registrationId) {
        Optional<CodeBuildEntity> entity = buildRepository.findByRegistrationId(registrationId);
        return entity.map(this::mapToBuildRegistration)
                .orElse(
                        CodeBuildRegistration
                                .newBuilder()
                                .error(Lists.newArrayList(Error.newBuilder().message("CodeBuildRegistration with registration ID %s does not exist.".formatted(registrationId)).build()))
                                .build());
    }

    @QueryMapping
    public CodeBuildResult getBuildOutput(@Argument String buildId, @Argument String sessionId) {
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
                .matchedOutput(history.getOutput())
                .error(parseErr(history.getError()))
                .exitCode(history.getExitCode())
                .buildId(buildId)
                .executionTime(history.getExecutionTimeMs())
                .artifactPaths(history.getArtifactPaths())
                .artifactOutputDirectory(history.getArtifactOutputDirectory())
                .buildLog(history.getBuildLog())
                .build();
    }

    private static @Nullable List<Error> parseErr(String error) {
        return StringUtils.isNotBlank(error) ? List.of(new Error(error)) : null;
    }

    // Deploy Queries
    @QueryMapping
    public List<CodeDeployRegistration> retrieveDeployRegistrations() {
        List<CodeDeployEntity> entities = deployRepository.findAll();
        return entities.stream()
                .map(this::mapToDeployRegistration)
                .collect(Collectors.toList());
    }

    @QueryMapping
    public List<CodeDeploy> retrieveDeploys() {
        List<CodeDeployHistory> entities = deployHistoryRepository.findTop10ByOrderByCreatedTimeDesc();
        return entities.stream()
                .map(this::mapToDeploy)
                .collect(Collectors.toList());
    }

    @QueryMapping
    public CodeDeployRegistration getCodeDeployRegistration(@Argument String registrationId) {
        Optional<CodeDeployEntity> entity = deployRepository.findByRegistrationId(registrationId);
        return entity.map(this::mapToDeployRegistration).orElse(null);
    }

    @QueryMapping
    public CodeDeployResult getDeployOutput(@Argument String deployId, @Argument String sessionId) {
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
                .matchedOutput(history.getOutput())
                .error(parseErr(history.getError()))
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

    @QueryMapping
    public List<CodeDeploy> getRunningDeployments() {
        List<CodeDeployHistory> entities = deployHistoryRepository.findByIsRunningTrue();
        return entities.stream()
                .map(this::mapToDeploy)
                .collect(Collectors.toList());
    }

    // Build Mutations
    @MutationMapping
    public CodeBuildRegistration registerCodeBuild(@Argument CodeBuildRegistrationIn codeBuildRegistration) {
        CodeBuildEntity entity = mapper.map(codeBuildRegistration, CodeBuildEntity.class);

        buildRepository.deleteById(entity.getRegistrationId());

        entity = buildRepository.save(entity);
        log.info("Registered new code build: {}", entity.getRegistrationId());

        return mapToBuildRegistration(entity);
    }

    @MutationMapping
    public CodeBuildRegistration updateCodeBuildRegistration(
            @Argument String registrationId,
            @Argument Boolean enabled,
            @Argument String buildCommand,
            @Argument String workingDirectory,
            @Argument String arguments,
            @Argument Integer timeoutSeconds,
            @Argument String sessionId) {

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

    @MutationMapping
    public Boolean deleteCodeBuildRegistration(@Argument String registrationId, @Argument String sessionId) {
        Optional<CodeBuildEntity> entityOpt = buildRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot delete - no code build registration found with ID: {}", registrationId);
            return false;
        }

        buildRepository.delete(entityOpt.get());
        log.info("Deleted code build registration: {}", registrationId);

        return true;
    }

    @MutationMapping
    public CodeBuildResult build(@Argument CodeBuildOptions options) {
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

    @MutationMapping
    public CodeDeployRegistration registerCodeDeploy(@Argument CodeDeployRegistrationIn codeDeployRegistration) {
        CodeDeployEntity entity = mapper.map(codeDeployRegistration, CodeDeployEntity.class);

        entity = deployRepository.save(entity);
        log.info("Registered new code deploy: {}", entity.getRegistrationId());

        return mapToDeployRegistration(entity);
    }

    @MutationMapping
    public CodeDeployRegistration updateCodeDeployRegistration(
            @Argument String registrationId,
            @Argument Boolean enabled,
            @Argument String deployCommand,
            @Argument String workingDirectory,
            @Argument String arguments,
            @Argument Integer timeoutSeconds,
            @Argument String sessionId) {

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

    @MutationMapping
    public Boolean deleteCodeDeployRegistration(@Argument String registrationId, @Argument String sessionId) {
        Optional<CodeDeployEntity> entityOpt = deployRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot delete - no code deploy registration found with ID: {}", registrationId);
            return false;
        }

        deployRepository.delete(entityOpt.get());
        log.info("Deleted code deploy registration: {}", registrationId);

        return true;
    }

    @MutationMapping
    public CodeDeployResult deploy(@Argument CodeDeployOptions options) {
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

    @MutationMapping
    public CodeDeployResult stopDeployment(@Argument String registrationId, @Argument String sessionId) {
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
                .matchedOutput(entity.getOutput())
                .error(parseErr(entity.getError()))
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
                .matchedOutput(entity.getOutput())
                .error(parseErr(entity.getError()))
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
