package com.hayden.functioncalling.runner.process_builder;

import com.hayden.commitdiffmodel.codegen.types.CodeDeployOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeDeployResult;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.functioncalling.entity.CodeDeployEntity;
import com.hayden.functioncalling.repository.CodeDeployRepository;
import com.hayden.functioncalling.runner.DeployExecRunner;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderDataService;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderDeployExecRunner implements DeployExecRunner {

    private final CodeDeployRepository codeDeployRepository;
    private final ProcessBuilderDataService deployDataService;
    private final ThreadPoolTaskExecutor runnerTaskExecutor;
    private final ThreadPoolTaskExecutor asyncRunnerTaskExecutor;

    @Override
    public CompletableFuture<CodeDeployResult> deployAsync(CodeDeployOptions codeDeployOptions) {
        return asyncRunnerTaskExecutor.submitCompletable(() -> this.deploy(codeDeployOptions));
    }

    @Override
    public CodeDeployResult deploy(CodeDeployOptions options) {
        if (options.getRegistrationId() == null) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("Registration ID is required")))
                    .build();
        }

        Optional<CodeDeployEntity> deployEntityOpt = codeDeployRepository.findByRegistrationId(options.getRegistrationId());

        if (deployEntityOpt.isEmpty()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("No code deploy registration found with ID: " + options.getRegistrationId())))
                    .build();
        }

        CodeDeployEntity deployEntity = deployEntityOpt.get();

        if (!deployEntity.getEnabled()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(options.getRegistrationId())
                    .error(List.of(new Error("Code deploy registration is disabled: " + options.getRegistrationId())))
                    .build();
        }

        try {
            return executeDeploy(deployEntity, options);
        } catch (Exception e) {
            log.error("Error executing deploy command", e);
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(options.getRegistrationId())
                    .error(List.of(new Error("Error executing deploy command: " + e.getMessage())))
                    .build();
        }
    }

    @Override
    public CodeDeployResult stopDeployment(String registrationId, String sessionId) {
        Optional<CodeDeployEntity> deployEntityOpt = codeDeployRepository.findByRegistrationId(registrationId);

        if (deployEntityOpt.isEmpty()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("No code deploy registration found with ID: " + registrationId)))
                    .build();
        }

        CodeDeployEntity deployEntity = deployEntityOpt.get();

        if (StringUtils.isBlank(deployEntity.getStopCommand())) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(registrationId)
                    .error(List.of(new Error("No stop command configured for deployment: " + registrationId)))
                    .build();
        }

        try {
            return executeStopCommand(deployEntity, sessionId);
        } catch (Exception e) {
            log.error("Error executing stop command", e);
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(registrationId)
                    .error(List.of(new Error("Error executing stop command: " + e.getMessage())))
                    .build();
        }
    }

    private CodeDeployResult executeDeploy(CodeDeployEntity entity, CodeDeployOptions options) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String deployId = UUID.randomUUID().toString();

        List<String> commandParts = new ArrayList<>(Arrays.asList(entity.getDeployCommand().split("\\s+")));

        // Add additional arguments if provided
        String arguments = null;
        if (StringUtils.isNotBlank(options.getArguments())) {
            arguments = options.getArguments();
            commandParts.addAll(Arrays.asList(options.getArguments().split("\\s+")));
        } else if (StringUtils.isNotBlank(entity.getArguments())) {
            arguments = entity.getArguments();
            commandParts.addAll(Arrays.asList(entity.getArguments().split("\\s+")));
        }

        log.info("Executing deploy command: {}", String.join(" ", commandParts));

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);

        // Set working directory if specified
        if (StringUtils.isNotBlank(entity.getWorkingDirectory())) {
            processBuilder.directory(new File(entity.getWorkingDirectory()));
        }

        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        int timeoutSeconds = options.getTimeoutSeconds() != null ? options.getTimeoutSeconds() :
                             entity.getTimeoutSeconds() != null ? entity.getTimeoutSeconds() : -1;

        // Read process output in real-time and wait for success patterns
        StringBuilder output = new StringBuilder();
        StringBuilder deployLog = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        boolean deploymentReady = false;
        boolean deploymentFailed = false;
        String error = null;

        // Start a thread to read the process output
        Thread outputThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    deployLog.append(line).append("\n");

                    if (entity.getOutputRegex() != null && !entity.getOutputRegex().isEmpty()) {
                        String finalLine = line;
                        if (entity.getOutputRegex().stream().anyMatch(finalLine::matches)) {
                            output.append(finalLine).append("\n");
                        }
                    } else {
                        output.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                log.error("Error reading deploy process output", e);
            }
        });

        var outputFuture = this.runnerTaskExecutor.submitCompletable(outputThread);

        // Wait for deployment success patterns or timeout
        int maxWaitSeconds = entity.getMaxWaitForStartupSeconds() != null ?
                            entity.getMaxWaitForStartupSeconds() :
                            (timeoutSeconds != -1 ? timeoutSeconds : 300); // Default 5 minutes

        long endTime = System.currentTimeMillis() + (maxWaitSeconds * 1000L);

        while (System.currentTimeMillis() < endTime && process.isAlive() && !deploymentReady && !deploymentFailed) {
            String currentOutput = deployLog.toString();

            // Check for success patterns
            if (entity.getDeploySuccessPatterns() != null && !entity.getDeploySuccessPatterns().isEmpty()) {
                deploymentReady = entity.getDeploySuccessPatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(currentOutput).find());
            }

            // Check for failure patterns
            if (entity.getDeployFailurePatterns() != null && !entity.getDeployFailurePatterns().isEmpty()) {
                deploymentFailed = entity.getDeployFailurePatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(currentOutput).find());
            }

            if (deploymentFailed) {
                error = "Deployment failure pattern detected in output";
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
            log.warn("Interrupted while waiting for deploy output thread to complete", e);
        }

        int exitCode = 0;
        boolean success = deploymentReady && !deploymentFailed;
        int executionTimeMs = (int)(System.currentTimeMillis() - startTime);

        if (!deploymentReady && !deploymentFailed) {
            if (!process.isAlive()) {
                exitCode = process.exitValue();
                if (exitCode != 0) {
                    error = "Deploy command exited with non-zero status: " + exitCode;
                    success = false;
                } else if (entity.getDeploySuccessPatterns() != null && !entity.getDeploySuccessPatterns().isEmpty()) {
                    error = "Deploy completed but success pattern not found in output";
                    success = false;
                } else {
                    success = true; // Process completed successfully and no patterns required
                }
            } else {
                error = "Deployment startup timed out after " + maxWaitSeconds + " seconds";
                success = false;
            }
        }

        // Perform health check if configured and deployment appears successful
        String healthCheckStatus = null;
        Integer healthCheckResponseTime = null;
        String deploymentUrl = null;

        if (success && StringUtils.isNotBlank(entity.getHealthCheckUrl())) {
            var healthCheck = performHealthCheck(entity);
            healthCheckStatus = healthCheck.status;
            healthCheckResponseTime = healthCheck.responseTime;
            deploymentUrl = entity.getHealthCheckUrl();

            if (!"HEALTHY".equals(healthCheckStatus)) {
                success = false;
                error = "Health check failed: " + healthCheckStatus;
            }
        }

        // Save deploy history
        deployDataService.saveDeployHistory(
                entity.getRegistrationId(), deployId, entity.getDeployCommand(), arguments,
                output.toString(), error, success, exitCode, executionTimeMs, options.getSessionId(),
                deployLog.toString(), healthCheckStatus, healthCheckResponseTime,
                process.isAlive(), deploymentUrl);

        return CodeDeployResult.newBuilder()
                .registrationId(options.getRegistrationId())
                .success(success)
                .output(output.toString())
                .sessionId(options.getSessionId())
                .exitCode(exitCode)
                .executionTime(executionTimeMs)
                .deployId(deployId)
                .error(List.of(new Error(error)))
                .deployLog(deployLog.toString())
                .healthCheckStatus(healthCheckStatus)
                .healthCheckResponseTime(healthCheckResponseTime)
                .isRunning(process.isAlive())
                .deploymentUrl(deploymentUrl)
                .build();
    }

    private CodeDeployResult executeStopCommand(CodeDeployEntity entity, String sessionId) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String deployId = UUID.randomUUID().toString();

        List<String> commandParts = new ArrayList<>(Arrays.asList(entity.getStopCommand().split("\\s+")));

        log.info("Executing stop command: {}", String.join(" ", commandParts));

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);

        // Set working directory if specified
        if (StringUtils.isNotBlank(entity.getWorkingDirectory())) {
            processBuilder.directory(new File(entity.getWorkingDirectory()));
        }

        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        // Read process output
        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        process.waitFor();

        int exitCode = process.exitValue();
        boolean success = (exitCode == 0);
        String error = success ? null : "Stop command exited with non-zero status: " + exitCode;
        int executionTimeMs = (int)(System.currentTimeMillis() - startTime);

        // Save deploy history for stop operation
        deployDataService.saveDeployHistory(
                entity.getRegistrationId(), deployId, entity.getStopCommand(), null,
                output.toString(), error, success, exitCode, executionTimeMs, sessionId,
                output.toString(), "STOPPED", null, false, null);

        return CodeDeployResult.newBuilder()
                .registrationId(entity.getRegistrationId())
                .success(success)
                .output(output.toString())
                .sessionId(sessionId)
                .exitCode(exitCode)
                .executionTime(executionTimeMs)
                .deployId(deployId)
                .error(List.of(new Error(error)))
                .deployLog(output.toString())
                .healthCheckStatus("STOPPED")
                .isRunning(false)
                .build();
    }

    private HealthCheckResult performHealthCheck(CodeDeployEntity entity) {
        long startTime = System.currentTimeMillis();
        int timeoutMs = entity.getHealthCheckTimeoutSeconds() != null ?
                       entity.getHealthCheckTimeoutSeconds() * 1000 :
                       10000; // Default 10 seconds

        try {
            URL url = new URL(entity.getHealthCheckUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);

            int responseCode = connection.getResponseCode();
            int responseTime = (int)(System.currentTimeMillis() - startTime);

            if (responseCode >= 200 && responseCode < 300) {
                return new HealthCheckResult("HEALTHY", responseTime);
            } else {
                return new HealthCheckResult("UNHEALTHY: HTTP " + responseCode, responseTime);
            }
        } catch (Exception e) {
            int responseTime = (int)(System.currentTimeMillis() - startTime);
            return new HealthCheckResult("UNHEALTHY: " + e.getMessage(), responseTime);
        }
    }

    private static class HealthCheckResult {
        final String status;
        final Integer responseTime;

        HealthCheckResult(String status, Integer responseTime) {
            this.status = status;
            this.responseTime = responseTime;
        }
    }
}
