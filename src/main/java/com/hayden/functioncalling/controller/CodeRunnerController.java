package com.hayden.functioncalling.controller;

import com.hayden.commitdiffmodel.codegen.types.*;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.commitdiffcontext.convert.CommitDiffContextMapper;
import com.hayden.functioncalling.entity.TestExecutionEntity;
import com.hayden.functioncalling.entity.TestExecutionHistory;
import com.hayden.functioncalling.repository.TestExecutionHistoryRepository;
import com.hayden.functioncalling.repository.TestExecutionRepository;
import com.hayden.functioncalling.runner.ExecRunner;
import com.netflix.graphql.dgs.DgsComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;

@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class CodeRunnerController {

    private final TestExecutionRepository executionRepository;
    private final TestExecutionHistoryRepository executionHistoryRepository;
    private final ExecRunner execRunner;
    private final CommitDiffContextMapper mapper;

    @QueryMapping
    public List<CodeExecutionRegistration> retrieveRegistrations() {
        List<TestExecutionEntity> entities = executionRepository.findAll();
        return entities.stream()
                .map(this::mapToRegistration)
                .collect(Collectors.toList());
    }
    
    @QueryMapping
    public List<CodeExecution> retrieveExecutions() {
        List<TestExecutionHistory> entities = executionHistoryRepository.findTop10ByOrderByCreatedTimeDesc();
        return entities.stream()
                .map(this::mapToExecution)
                .collect(Collectors.toList());
    }

    @QueryMapping
    public CodeExecutionRegistration getCodeExecutionRegistration(@Argument String registrationId) {
        Optional<TestExecutionEntity> entity = executionRepository.findByRegistrationId(registrationId);
        return entity.map(this::mapToRegistration).orElse(null);
    }

    @MutationMapping
    public CodeExecutionRegistration registerCodeExecution(
            @Argument CodeExecutionRegistrationIn codeExecutionRegistration) {
        TestExecutionEntity entity = mapper.map(codeExecutionRegistration, TestExecutionEntity.class);
        entity.setExecutionType(Optional.ofNullable(entity.getExecutionType()).orElse(ExecutionType.PROCESS_BUILDER));
        
        entity = executionRepository.save(entity);
        log.info("Registered new code execution: {}", entity.getRegistrationId());
        
        return mapToRegistration(entity);
    }

    @MutationMapping
    
    public CodeExecutionRegistration updateCodeExecutionRegistration(
            @Argument String registrationId,
            @Argument Boolean enabled,
            @Argument String command,
            @Argument String workingDirectory,
            @Argument String arguments,
            @Argument Integer timeoutSeconds,
            @Argument String sessionId) {
        
        Optional<TestExecutionEntity> entityOpt = executionRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot update - no code execution registration found with ID: {}", registrationId);
            return null;
        }
        
        TestExecutionEntity entity = entityOpt.get();
        entity.setExecutionType(Optional.ofNullable(entity.getExecutionType()).orElse(ExecutionType.PROCESS_BUILDER));

        entity.setSessionId(sessionId);
        
        if (enabled != null) {
            entity.setEnabled(enabled);
        }
        
        if (command != null) {
            entity.setCommand(command);
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
        
        entity = executionRepository.save(entity);
        log.info("Updated code execution registration: {}", entity.getRegistrationId());
        
        return mapToRegistration(entity);
    }

    @MutationMapping
    
    public Boolean deleteCodeExecutionRegistration(@Argument String registrationId, @Argument String sessionId) {
        Optional<TestExecutionEntity> entityOpt = executionRepository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            log.warn("Cannot delete - no code execution registration found with ID: {}", registrationId);
            return false;
        }

        executionRepository.delete(entityOpt.get());
        log.info("Deleted code execution registration: {}", registrationId);
        
        return true;
    }

    @MutationMapping
    public CodeExecutionResult execute(@Argument CodeExecutionOptions options) {
        log.info("Executing code with options: {}", options);
        
        if (options == null || options.getRegistrationId() == null) {
            return CodeExecutionResult.newBuilder()
                    .success(false)
                    .sessionId(options.getSessionId())
                    .error(List.of(new Error("Invalid execution options. Registration ID is required.")))
                    .build();
        }
        
        return execRunner.run(options);
    }
    
    @MutationMapping
    public CodeExecutionResult executeWithOutputFile(@Argument CodeExecutionOptions options, @Argument String outputFilePath) {
        log.info("Executing code with output file. Options: {}, File: {}", options, outputFilePath);
        
        if (options == null || options.getRegistrationId() == null) {
            return CodeExecutionResult.newBuilder()
                    .success(false)
                    .sessionId(options.getSessionId())
                    .error(List.of(new Error("Invalid execution options. Registration ID is required.")))
                    .build();
        }
        
        // Set the file writing flag and path
        CodeExecutionOptions modifiedOptions = CodeExecutionOptions.newBuilder()
                .registrationId(options.getRegistrationId())
                .sessionId(options.getSessionId())
                .sessionId(options.getSessionId())
                .arguments(options.getArguments())
                .timeoutSeconds(options.getTimeoutSeconds())
                .writeToFile(true)
                .outputFilePath(outputFilePath)
                .build();
        
        return execRunner.run(modifiedOptions);
    }
    
    private CodeExecutionRegistration mapToRegistration(TestExecutionEntity entity) {
        return CodeExecutionRegistration.newBuilder()
                .registrationId(entity.getRegistrationId())
                .command(entity.getCommand())
                .executionType(Optional.ofNullable(entity.getExecutionType()).orElse(ExecutionType.PROCESS_BUILDER))
                .workingDirectory(entity.getWorkingDirectory())
                .description(entity.getDescription())
                .arguments(entity.getArguments())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .enabled(entity.getEnabled())
                .build();
    }
    
    private CodeExecution mapToExecution(TestExecutionHistory entity) {
        return CodeExecution.newBuilder()
                .executionType(Optional.ofNullable(entity.getExecutionType()).orElse(ExecutionType.PROCESS_BUILDER))
                .sessionId(entity.getSessionId())
                .registrationId(entity.getExecutionId())
                .command(entity.getCommand() + (entity.getArguments() != null ? " " + entity.getArguments() : ""))
                .status(entity.getSuccess() ? "SUCCESS" : "FAILED")
                .startTime(convertToDate(entity.getExecutionTimeMs()))
                .endTime(entity.getExecutionTimeMs() != null
                         ? LocalDate.ofInstant(Instant.ofEpochMilli(entity.getExecutionTimeMs())
                                 .plusMillis(entity.getExecutionTimeMs()), ZoneId.systemDefault())
                         : null)
                .exitCode(entity.getExitCode())
                .output(entity.getOutput())
                .error(List.of(new Error(entity.getError())))
                .build();
    }

    private LocalDate convertToDate(Integer localDateTime) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(localDateTime), ZoneId.systemDefault()) ;
    }

    @QueryMapping
    public CodeExecutionResult getExecutionOutput(@Argument String executionId, @Argument String sessionId) {
        if (executionId == null || executionId.isBlank()) {
            return CodeExecutionResult.newBuilder()
                    .success(false)
                    .sessionId(sessionId)
                    .error(List.of(new Error("Execution ID is required")))
                    .build();
        }
        
        // Find the execution history
        Optional<TestExecutionHistory> historyOpt = executionHistoryRepository.findByExecutionId(executionId);
        
        if (historyOpt.isEmpty()) {
            return CodeExecutionResult.newBuilder()
                    .sessionId(sessionId)
                    .success(false)
                    .error(List.of(new Error("No execution found with ID: " + executionId)))
                    .build();
        }
        
        TestExecutionHistory history = historyOpt.get();
        
        return CodeExecutionResult.newBuilder()
                .sessionId(sessionId)
                .success(history.getSuccess())
                .output(history.getOutput())
                .error(List.of(new Error(history.getError())))
                .exitCode(history.getExitCode())
                .executionId(executionId)
                .executionTime(history.getExecutionTimeMs())
                .build();
    }
    
    // No subscription methods needed as we've removed all streaming functionality
}
