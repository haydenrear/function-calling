package com.hayden.functioncalling.service.process_builder.exec;

import com.hayden.commitdiffmodel.codegen.types.CodeDeployOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeDeployResult;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.functioncalling.entity.CodeDeployEntity;
import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import com.hayden.functioncalling.service.ExecutionService;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderDataService;
import com.hayden.functioncalling.service.process_builder.ProcessExecutionRequest;
import com.hayden.functioncalling.service.process_builder.ProcessExecutionResult;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderDeployExecutionService implements ExecutionService<CodeDeployEntity, CodeDeployResult, CodeDeployOptions> {

    private final ProcessBuilderExecutionService processBuilderService;
    private final ProcessBuilderDataService deployDataService;

    @Override
    public CodeDeployResult execute(CodeDeployEntity entity, CodeDeployOptions options) {
        try {
            return executeDeploy(entity, options);
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
    public ExecutionType getExecutionType() {
        return ExecutionType.PROCESS_BUILDER;
    }

    public CodeDeployResult stopDeployment(CodeDeployEntity entity, String sessionId) {
        if (StringUtils.isBlank(entity.getStopCommand())) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(entity.getRegistrationId())
                    .error(List.of(new Error("No stop command configured for deployment: " + entity.getRegistrationId())))
                    .build();
        }

        try {
            return executeStopCommand(entity, sessionId);
        } catch (Exception e) {
            log.error("Error executing stop command", e);
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(entity.getRegistrationId())
                    .error(List.of(new Error("Error executing stop command: " + e.getMessage())))
                    .build();
        }
    }

    private CodeDeployResult executeDeploy(CodeDeployEntity entity, CodeDeployOptions options) throws Exception {
        String deployId = UUID.randomUUID().toString();

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

        // Build process execution request
        ProcessExecutionRequest request = ProcessExecutionRequest.builder()
                .command(entity.getDeployCommand())
                .arguments(arguments)
                .workingDirectory(entity.getWorkingDirectory())
                .timeoutSeconds(timeoutSeconds)
                .outputRegex(entity.getOutputRegex())
                .successPatterns(entity.getDeploySuccessPatterns())
                .failurePatterns(entity.getDeployFailurePatterns())
                .maxWaitForPatternSeconds(entity.getMaxWaitForStartupSeconds())
                .build();

        // Execute using ProcessBuilderService with pattern wait
        ProcessExecutionResult result = processBuilderService.executeProcessWithPatternWait(request);

        // Perform health check if configured and deployment appears successful
        String healthCheckStatus = null;
        Integer healthCheckResponseTime = null;
        String deploymentUrl = null;

        if (result.isSuccess() && StringUtils.isNotBlank(entity.getHealthCheckUrl())) {
            var healthCheck = performHealthCheck(entity);
            healthCheckStatus = healthCheck.status;
            healthCheckResponseTime = healthCheck.responseTime;
            deploymentUrl = entity.getHealthCheckUrl();

            if (!"HEALTHY".equals(healthCheckStatus)) {
                result = ProcessExecutionResult.builder()
                        .success(false)
                        .output(result.getOutput())
                        .fullLog(result.getFullLog())
                        .error("Health check failed: " + healthCheckStatus)
                        .exitCode(result.getExitCode())
                        .executionTimeMs(result.getExecutionTimeMs())
                        .process(result.getProcess())
                        .build();
            }
        }

        // Save deploy history
        deployDataService.saveDeployHistory(
                entity.getRegistrationId(),
                deployId,
                entity.getDeployCommand(),
                arguments,
                result.getOutput(),
                result.getError(),
                result.isSuccess(),
                result.getExitCode(),
                result.getExecutionTimeMs(),
                options.getSessionId(),
                result.getFullLog(),
                healthCheckStatus,
                healthCheckResponseTime,
                result.getProcess() != null && result.getProcess().isAlive(),
                deploymentUrl
        );

        return CodeDeployResult.newBuilder()
                .registrationId(options.getRegistrationId())
                .success(result.isSuccess())
                .output(result.getOutput())
                .sessionId(options.getSessionId())
                .exitCode(result.getExitCode())
                .executionTime(result.getExecutionTimeMs())
                .deployId(deployId)
                .error(List.of(new Error(result.getError())))
                .deployLog(result.getFullLog())
                .healthCheckStatus(healthCheckStatus)
                .healthCheckResponseTime(healthCheckResponseTime)
                .isRunning(result.getProcess() != null && result.getProcess().isAlive())
                .deploymentUrl(deploymentUrl)
                .build();
    }

    public CodeDeployResult executeStopCommand(CodeDeployEntity entity, String sessionId) throws Exception {
        String deployId = UUID.randomUUID().toString();

        ProcessExecutionRequest request = ProcessExecutionRequest.builder()
                .command(entity.getStopCommand())
                .workingDirectory(entity.getWorkingDirectory())
                .timeoutSeconds(entity.getTimeoutSeconds())
                .build();

        ProcessExecutionResult result = processBuilderService.executeProcess(request);

        // Save deploy history for stop operation
        deployDataService.saveDeployHistory(
                entity.getRegistrationId(),
                deployId,
                entity.getStopCommand(),
                null,
                result.getOutput(),
                result.getError(),
                result.isSuccess(),
                result.getExitCode(),
                result.getExecutionTimeMs(),
                sessionId,
                result.getFullLog(),
                "STOPPED",
                null,
                false,
                null
        );

        return CodeDeployResult.newBuilder()
                .registrationId(entity.getRegistrationId())
                .success(result.isSuccess())
                .output(result.getOutput())
                .sessionId(sessionId)
                .exitCode(result.getExitCode())
                .executionTime(result.getExecutionTimeMs())
                .deployId(deployId)
                .error(List.of(new Error(result.getError())))
                .deployLog(result.getFullLog())
                .healthCheckStatus("STOPPED")
                .isRunning(false)
                .build();
    }

    private HealthCheckResult performHealthCheck(CodeDeployEntity entity) {
        long startTime = System.currentTimeMillis();
        int timeoutMs = entity.getHealthCheckTimeoutSeconds() != null
                ? entity.getHealthCheckTimeoutSeconds() * 1000
                : 10000; // Default 10 seconds

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
