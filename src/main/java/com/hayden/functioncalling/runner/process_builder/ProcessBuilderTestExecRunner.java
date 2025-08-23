package com.hayden.functioncalling.runner.process_builder;

import com.hayden.commitdiffmodel.codegen.types.CodeExecutionOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionResult;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import com.hayden.functioncalling.context_processor.TestReportService;
import com.hayden.functioncalling.entity.TestExecutionEntity;
import com.hayden.functioncalling.repository.TestExecutionRepository;
import com.hayden.functioncalling.runner.ExecRunner;
import com.hayden.functioncalling.service.ExecutionService;
import com.hayden.functioncalling.service.process_builder.*;
import com.hayden.functioncalling.service.process_builder.exec.ProcessBuilderExecutionService;
import com.hayden.utilitymodule.stream.StreamUtil;
import io.micrometer.common.util.StringUtils;
import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderTestExecRunner
    implements
        ExecRunner,
        ExecutionService<
                TestExecutionEntity,
            CodeExecutionResult,
            CodeExecutionOptions
        > {

    private final TestExecutionRepository testExecutionRepository;
    private final ProcessBuilderDataService executionDataService;
    private final ThreadPoolTaskExecutor asyncRunnerTaskExecutor;
    private final TestReportService testReportService;
    private final ProcessBuilderExecutionService processBuilderService;

    @Override
    public CompletableFuture<CodeExecutionResult> runAsync(
        CodeExecutionOptions codeExecutionResult
    ) {
        return asyncRunnerTaskExecutor.submitCompletable(() ->
            this.run(codeExecutionResult)
        );
    }

    @Override
    public CodeExecutionResult run(CodeExecutionOptions options) {
        if (options.getRegistrationId() == null) {
            return CodeExecutionResult.newBuilder()
                .success(false)
                .error(List.of(new Error("Registration ID is required")))
                .build();
        }

        Optional<TestExecutionEntity> executionEntityOpt =
            testExecutionRepository.findByRegistrationId(
                options.getRegistrationId()
            );

        if (executionEntityOpt.isEmpty()) {
            return CodeExecutionResult.newBuilder()
                .success(false)
                .error(
                    List.of(
                        new Error(
                            "No code execution registration found with ID: " +
                            options.getRegistrationId()
                        )
                    )
                )
                .build();
        }

        TestExecutionEntity executionEntity = executionEntityOpt.get();

        if (!executionEntity.getEnabled()) {
            return CodeExecutionResult.newBuilder()
                .success(false)
                .registrationId(options.getRegistrationId())
                .error(
                    List.of(
                        new Error(
                            "Code execution registration is disabled: " +
                            options.getRegistrationId()
                        )
                    )
                )
                .build();
        }

        return execute(executionEntity, options);
    }

    @Override
    public CodeExecutionResult execute(
        TestExecutionEntity entity,
        CodeExecutionOptions options
    ) {
        try {
            return executeCommand(entity, options);
        } catch (Exception e) {
            log.error("Error executing command", e);
            return CodeExecutionResult.newBuilder()
                .success(false)
                .registrationId(options.getRegistrationId())
                .error(
                    List.of(
                        new Error("Error executing command: " + e.getMessage())
                    )
                )
                .build();
        }
    }

    @Override
    public ExecutionType getExecutionType() {
        return ExecutionType.PROCESS_BUILDER;
    }

    private CodeExecutionResult executeCommand(
        TestExecutionEntity entity,
        CodeExecutionOptions options
    ) throws IOException, InterruptedException {
        String executionId = UUID.randomUUID().toString();

        // Determine arguments
        String arguments = null;
        if (StringUtils.isNotBlank(options.getArguments())) {
            arguments = options.getArguments();
        } else if (StringUtils.isNotBlank(entity.getArguments())) {
            arguments = entity.getArguments();
        }

        // Determine timeout
        Integer timeoutSeconds = options.getTimeoutSeconds() != null
            ? options.getTimeoutSeconds()
            : entity.getTimeoutSeconds() != null
                ? entity.getTimeoutSeconds()
                : null;

        // Determine output file
        File outputFile = null;
        String outputFilePath = null;
        boolean writeToFile = Boolean.TRUE.equals(options.getWriteToFile());
        if (writeToFile) {
            outputFilePath = options.getOutputFilePath();
            if (outputFilePath == null || outputFilePath.isBlank()) {
                outputFilePath = "execution_" + executionId + ".log";
            }
            outputFile = new File(outputFilePath);
            log.info("Writing execution output to file: {}", outputFilePath);
        }

        // Build process execution request
        ProcessExecutionRequest request = ProcessExecutionRequest.builder()
            .command(entity.getCommand())
            .arguments(arguments)
            .workingDirectory(entity.getWorkingDirectory())
            .timeoutSeconds(timeoutSeconds)
            .outputRegex(entity.getOutputRegex())
            .outputFile(outputFile)
            .build();

        // Execute using ProcessBuilderService
        ProcessExecutionResult result = processBuilderService.executeProcess(
            request
        );

        // Get test reporting if configured
        var reporting = StreamUtil.toStream(entity.getReportingPaths())
            .flatMap(s -> {
                try {
                    return Stream.ofNullable(
                        testReportService.getContext(
                            s,
                            entity.getRegistrationId(),
                            options.getSessionId()
                        )
                    );
                } catch (RuntimeException e) {
                    return Stream.empty();
                }
            })
            .collect(Collectors.joining(System.lineSeparator()));

        var outputStr = result.getOutput();
        if (StringUtils.isNotBlank(reporting)) {
            outputStr = reporting;
        }

        // Save execution history
        executionDataService.saveExecutionHistory(
            entity.getRegistrationId(),
            executionId,
            entity.getCommand(),
            arguments,
            outputStr,
            result.getError(),
            result.isSuccess(),
            result.getExitCode(),
            result.getExecutionTimeMs(),
            options.getSessionId()
        );

        return CodeExecutionResult.newBuilder()
            .registrationId(options.getRegistrationId())
            .success(result.isSuccess())
            .output(result.getOutput())
            .sessionId(options.getSessionId())
            .exitCode(result.getExitCode())
            .executionTime(result.getExecutionTimeMs())
            .executionId(executionId)
            .error(List.of(new Error(result.getError())))
            .outputFile(writeToFile ? outputFilePath : null)
            .build();
    }
}
