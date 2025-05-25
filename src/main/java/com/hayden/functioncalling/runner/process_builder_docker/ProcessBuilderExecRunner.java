package com.hayden.functioncalling.runner.process_builder_docker;

import com.hayden.commitdiffmodel.codegen.types.CodeExecutionOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionResult;
import com.hayden.functioncalling.entity.CodeExecutionEntity;
import com.hayden.functioncalling.repository.CodeExecutionHistoryRepository;
import com.hayden.functioncalling.repository.CodeExecutionRepository;
import com.hayden.functioncalling.runner.ExecRunner;
import com.hayden.functioncalling.service.ExecutionDataService;
import com.hayden.functioncalling.service.TestReportService;
import com.hayden.utilitymodule.stream.StreamUtil;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderExecRunner implements ExecRunner {
    
    private final CodeExecutionRepository codeExecutionRepository;
    private final ExecutionDataService executionDataService;
    private final ThreadPoolTaskExecutor runnerTaskExecutor;
    private final ThreadPoolTaskExecutor asyncRunnerTaskExecutor;
    private final TestReportService testReportService;

    @Override
    public CompletableFuture<CodeExecutionResult> runAsync(CodeExecutionOptions codeExecutionResult) {
        return asyncRunnerTaskExecutor.submitCompletable(() -> this.run(codeExecutionResult));
    }

    @Override
    public CodeExecutionResult run(CodeExecutionOptions options) {
        if (options.getRegistrationId() == null) {
            return CodeExecutionResult.newBuilder()
                    .success(false)
                    .error("Registration ID is required")
                    .build();
        }
        
        Optional<CodeExecutionEntity> executionEntityOpt = codeExecutionRepository.findByRegistrationId(options.getRegistrationId());
        
        if (executionEntityOpt.isEmpty()) {
            return CodeExecutionResult.newBuilder()
                    .success(false)
                    .error("No code execution registration found with ID: " + options.getRegistrationId())
                    .build();
        }
        
        CodeExecutionEntity executionEntity = executionEntityOpt.get();
        
        if (!executionEntity.getEnabled()) {
            return CodeExecutionResult.newBuilder()
                    .success(false)
                    .error("Code execution registration is disabled: " + options.getRegistrationId())
                    .build();
        }
        
        try {
            return executeCommand(executionEntity, options);
        } catch (Exception e) {
            log.error("Error executing command", e);
            return CodeExecutionResult.newBuilder()
                    .success(false)
                    .error("Error executing command: " + e.getMessage())
                    .build();
        }
    }


    private CodeExecutionResult executeCommand(CodeExecutionEntity entity, CodeExecutionOptions options) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();

        List<String> commandParts = new ArrayList<>(Arrays.asList(entity.getCommand().split("\\s+")));
        
        // Add additional arguments if provided
        String arguments = null;
        if (StringUtils.isNotBlank(options.getArguments())) {
            arguments = options.getArguments();
            commandParts.addAll(Arrays.asList(options.getArguments().split("\\s+")));
        } else if (StringUtils.isNotBlank(entity.getArguments())) {
            arguments = entity.getArguments();
            commandParts.addAll(Arrays.asList(entity.getArguments().split("\\s+")));
        }
        
        log.info("Executing command: {}", String.join(" ", commandParts));
        
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        
        // Set working directory if specified
        if (StringUtils.isNotBlank(entity.getWorkingDirectory())) {
            processBuilder.directory(new File(entity.getWorkingDirectory()));
        }
        
        // Determine if we should write to a file
        boolean writeToFile = Boolean.TRUE.equals(options.getWriteToFile());
        String outputFilePath = options.getOutputFilePath();
        File outputFile;
        
        if (writeToFile) {
            if (outputFilePath == null || outputFilePath.isBlank()) {
                // Generate a default output file path if none provided
                outputFilePath = "execution_" + executionId + ".log";
            }
            
            outputFile = new File(outputFilePath);
            // Create parent directories if they don't exist
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            
            // Create empty file to start with
            Files.writeString(outputFile.toPath(), "");
            log.info("Writing execution output to file: {}", outputFilePath);
        } else {
            outputFile = null;
        }

        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        int timeoutSeconds = options.getTimeoutSeconds() != null ? options.getTimeoutSeconds() :
                             entity.getTimeoutSeconds() != null ? entity.getTimeoutSeconds() : -1;
        
        // Read process output in real-time
        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        
        // Start a thread to read the process output
        Thread outputThread = new Thread(() -> {
            try {
                String line;
                writeToLog(writeToFile, outputFile, "Starting output for %s".formatted(entity.getRegistrationId()) + "\n");
                while ((line = reader.readLine()) != null) {

                    if (entity.getOutputRegex() != null && !entity.getOutputRegex().isEmpty()) {
                        String finalLine = line;
                        if (entity.getOutputRegex().stream().anyMatch(finalLine::matches)) {
                            output.append(finalLine).append("\n");
                        }
                    } else {
                        output.append(line).append("\n");
                    }

                    // If we're writing to a file, append each line as it comes
                    writeToLog(writeToFile, outputFile, line + "\n");
                }
            } catch (IOException e) {
                log.error("Error reading process output", e);
            }
        });

        var out = this.runnerTaskExecutor.submitCompletable(outputThread);

        // Wait for the process to complete
        boolean completed;
        if (timeoutSeconds != -1)
             completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        else
            completed = process.waitFor() == 0;
        
        // Wait for the output thread to finish reading
        try {
            out.get(1000, TimeUnit.MILLISECONDS); // Wait up to 1 second for the thread to finish
        } catch (InterruptedException |
                 ExecutionException |
                 TimeoutException e) {
            log.warn("Interrupted while waiting for output thread to complete", e);
        }
        
        int exitCode;
        String error = null;
        boolean success;
        int executionTimeMs = (int)(System.currentTimeMillis() - startTime);
        
        if (!completed) {
            process.destroyForcibly();
            error = "Command execution timed out after " + timeoutSeconds + " seconds";
            success = false;
            exitCode = -1;
            
            // Add the timeout message to the output file if we're writing to one
            writeToLog(writeToFile, outputFile, "\n" + error + "\n");
        } else {
            exitCode = process.exitValue();
            success = (exitCode == 0);
            if (!success) {
                error = "Command exited with non-zero status: " + exitCode;
                
                // Add the error message to the output file if we're writing to one
                writeToLog(writeToFile, outputFile, "\n" + error + "\n");
            }
        }

        var reporting = StreamUtil.toStream(entity.getReportingPaths())
                .flatMap(s -> {
                    try {
                        return Stream.ofNullable(testReportService.getFailureContext(s));
                    } catch (RuntimeException e) {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.joining(System.lineSeparator()));

        var outputStr = output.toString();

        if (StringUtils.isNotBlank(reporting)) {
            outputStr = reporting;
        }

        // Save execution history
        executionDataService.saveExecutionHistory(
                entity.getRegistrationId(), executionId, entity.getCommand(), arguments,
                outputStr, error, success, exitCode, executionTimeMs);

        return CodeExecutionResult.newBuilder()
                .success(success)
                .output(output.toString())
                .exitCode(exitCode)
                .executionTime(executionTimeMs)
                .executionId(executionId)
                .error(error)
                .outputFile(writeToFile ? outputFilePath : null)
                .build();
    }

    private static void writeToLog(boolean writeToFile, File outputFile, String entity) {
        if (writeToFile && outputFile != null) {
            try {
                Files.writeString(outputFile.toPath(), entity, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.error("Error writing to file", e);
            }
        } else {
            log.info("{}", entity);
        }
    }

}
