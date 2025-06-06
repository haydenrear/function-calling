package com.hayden.functioncalling.service.process_builder.exec;

import com.hayden.functioncalling.service.process_builder.ProcessExecutionRequest;
import com.hayden.functioncalling.service.process_builder.ProcessExecutionResult;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderExecutionService {

    private final ThreadPoolTaskExecutor runnerTaskExecutor;

    public ProcessExecutionResult executeProcess(ProcessExecutionRequest request) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        List<String> commandParts = buildCommandParts(request.getCommand(), request.getArguments());
        log.info("Executing command: {}", String.join(" ", commandParts));

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);

        // Set working directory if specified
        if (StringUtils.isNotBlank(request.getWorkingDirectory())) {
            processBuilder.directory(new File(request.getWorkingDirectory()));
        }

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Read process output in real-time
        StringBuilder output = new StringBuilder();
        StringBuilder fullLog = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // Start a thread to read the process output
        Thread outputThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullLog.append(line).append("\n");

                    // Apply output regex filtering if specified
                    if (request.getOutputRegex() != null && !request.getOutputRegex().isEmpty()) {
                        String finalLine = line;
                        if (request.getOutputRegex().stream().anyMatch(finalLine::matches)) {
                            output.append(finalLine).append("\n");
                        }
                    } else {
                        output.append(line).append("\n");
                    }

                    // Write to file if specified
                    if (request.getOutputFile() != null) {
                        writeToFile(request.getOutputFile(), line + "\n");
                    }
                }
            } catch (IOException e) {
                log.error("Error reading process output", e);
            }
        });

        CompletableFuture<Void> outputFuture = runnerTaskExecutor.submitCompletable(outputThread);

        // Wait for the process to complete
        boolean completed = waitForProcess(process, request.getTimeoutSeconds());

        // Wait for the output thread to finish reading
        try {
            outputFuture.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("Interrupted while waiting for output thread to complete", e);
        }

        int exitCode;
        String error = null;
        boolean success;
        int executionTimeMs = (int)(System.currentTimeMillis() - startTime);

        if (!completed) {
            process.destroyForcibly();
            error = "Command execution timed out after " + request.getTimeoutSeconds() + " seconds";
            success = false;
            exitCode = -1;

            if (request.getOutputFile() != null) {
                writeToFile(request.getOutputFile(), "\n" + error + "\n");
            }
        } else {
            exitCode = process.exitValue();
            success = (exitCode == 0);

            // Check for success/failure patterns
            String logOutput = fullLog.toString();
            if (request.getSuccessPatterns() != null && !request.getSuccessPatterns().isEmpty()) {
                boolean foundSuccessPattern = request.getSuccessPatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(logOutput).find());
                if (!foundSuccessPattern && success) {
                    success = false;
                    error = "Process completed but success pattern not found in output";
                }
            }

            if (request.getFailurePatterns() != null && !request.getFailurePatterns().isEmpty()) {
                boolean foundFailurePattern = request.getFailurePatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(logOutput).find());
                if (foundFailurePattern) {
                    success = false;
                    error = "Process failure pattern detected in output";
                }
            }

            if (!success && error == null) {
                error = "Command exited with non-zero status: " + exitCode;
            }

            if (request.getOutputFile() != null && error != null) {
                writeToFile(request.getOutputFile(), "\n" + error + "\n");
            }
        }

        return ProcessExecutionResult.builder()
                .success(success)
                .output(output.toString())
                .fullLog(fullLog.toString())
                .error(error)
                .exitCode(exitCode)
                .executionTimeMs(executionTimeMs)
                .process(process)
                .build();
    }

    public ProcessExecutionResult executeProcessWithPatternWait(ProcessExecutionRequest request) throws IOException {
        long startTime = System.currentTimeMillis();

        List<String> commandParts = buildCommandParts(request.getCommand(), request.getArguments());
        log.info("Executing command with pattern wait: {}", String.join(" ", commandParts));

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);

        if (StringUtils.isNotBlank(request.getWorkingDirectory())) {
            processBuilder.directory(new File(request.getWorkingDirectory()));
        }

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        StringBuilder fullLog = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        boolean patternFound = false;
        boolean failureFound = false;
        String error = null;

        Thread outputThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullLog.append(line).append("\n");

                    if (request.getOutputRegex() != null && !request.getOutputRegex().isEmpty()) {
                        String finalLine = line;
                        if (request.getOutputRegex().stream().anyMatch(finalLine::matches)) {
                            output.append(finalLine).append("\n");
                        }
                    } else {
                        output.append(line).append("\n");
                    }

                    if (request.getOutputFile() != null) {
                        writeToFile(request.getOutputFile(), line + "\n");
                    }
                }
            } catch (IOException e) {
                log.error("Error reading process output", e);
            }
        });

        CompletableFuture<Void> outputFuture = runnerTaskExecutor.submitCompletable(outputThread);

        // Wait for patterns with timeout
        int maxWaitSeconds = request.getMaxWaitForPatternSeconds() != null ?
                            request.getMaxWaitForPatternSeconds() :
                            (request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 300);

        long endTime = System.currentTimeMillis() + (maxWaitSeconds * 1000L);

        while (System.currentTimeMillis() < endTime && process.isAlive() && !patternFound && !failureFound) {
            String currentOutput = fullLog.toString();

            // Check for success patterns
            if (request.getSuccessPatterns() != null && !request.getSuccessPatterns().isEmpty()) {
                patternFound = request.getSuccessPatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(currentOutput).find());
            }

            // Check for failure patterns
            if (request.getFailurePatterns() != null && !request.getFailurePatterns().isEmpty()) {
                failureFound = request.getFailurePatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(currentOutput).find());
            }

            if (failureFound) {
                error = "Failure pattern detected in output";
                break;
            }

            try {
                Thread.sleep(1000); // Check every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Wait for the output thread to finish reading
        try {
            outputFuture.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("Interrupted while waiting for output thread to complete", e);
        }

        int exitCode = 0;
        boolean success = patternFound && !failureFound;
        int executionTimeMs = (int)(System.currentTimeMillis() - startTime);

        if (!patternFound && !failureFound) {
            if (!process.isAlive()) {
                exitCode = process.exitValue();
                if (exitCode != 0) {
                    error = "Process exited with non-zero status: " + exitCode;
                    success = false;
                } else if (request.getSuccessPatterns() != null && !request.getSuccessPatterns().isEmpty()) {
                    error = "Process completed but success pattern not found in output";
                    success = false;
                } else {
                    success = true;
                }
            } else {
                error = "Pattern wait timed out after " + maxWaitSeconds + " seconds";
                success = false;
            }
        }

        if (request.getOutputFile() != null && error != null) {
            writeToFile(request.getOutputFile(), "\n" + error + "\n");
        }

        return ProcessExecutionResult.builder()
                .success(success)
                .output(output.toString())
                .fullLog(fullLog.toString())
                .error(error)
                .exitCode(exitCode)
                .executionTimeMs(executionTimeMs)
                .process(process)
                .build();
    }

    private List<String> buildCommandParts(String command, String arguments) {
        List<String> commandParts = new ArrayList<>(Arrays.asList(command.split("\\s+")));

        if (StringUtils.isNotBlank(arguments)) {
            commandParts.addAll(Arrays.asList(arguments.split("\\s+")));
        }

        return commandParts;
    }

    private boolean waitForProcess(Process process, Integer timeoutSeconds) throws InterruptedException {
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            return process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            return process.waitFor() == 0;
        }
    }

    private void writeToFile(File outputFile, String content) {
        try {
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(outputFile, true)) {
                writer.write(content);
            }
        } catch (IOException e) {
            log.error("Error writing to file: {}", outputFile.getAbsolutePath(), e);
        }
    }
}
