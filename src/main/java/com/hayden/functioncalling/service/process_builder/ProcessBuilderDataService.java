package com.hayden.functioncalling.service.process_builder;

import com.hayden.functioncalling.entity.CodeBuildHistory;
import com.hayden.functioncalling.entity.CodeDeployHistory;
import com.hayden.functioncalling.entity.TestExecutionHistory;
import com.hayden.functioncalling.repository.CodeBuildHistoryRepository;
import com.hayden.functioncalling.repository.CodeDeployHistoryRepository;
import com.hayden.functioncalling.repository.TestExecutionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderDataService {

    private final CodeBuildHistoryRepository buildHistoryRepository;
    private final CodeDeployHistoryRepository deployHistoryRepository;

    private final TestExecutionHistoryRepository executionHistoryRepository;


    public void saveExecutionHistory(String registrationId, String executionId, String command, String arguments,
                                     String output, String error, boolean success, int exitCode, int executionTimeMs, String sessionId) {
        try {
            TestExecutionHistory history = TestExecutionHistory.builder()
                    .registrationId(registrationId)
                    .sessionId(sessionId)
                    .executionId(executionId)
                    .command(command)
                    .arguments(arguments)
                    .output(output)
                    .error(error)
                    .success(success)
                    .exitCode(exitCode)
                    .executionTimeMs(executionTimeMs)
                    .build();

            executionHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to save execution history", e);
        }
    }

    public void saveBuildHistory(String registrationId, String buildId, String buildCommand, String arguments,
                                String output, String error, boolean success, int exitCode, int executionTimeMs,
                                String sessionId, List<String> artifactPaths, String artifactOutputDirectory,
                                String buildLog) {
        try {
            CodeBuildHistory history = CodeBuildHistory.builder()
                    .registrationId(registrationId)
                    .sessionId(sessionId)
                    .buildId(buildId)
                    .buildCommand(buildCommand)
                    .arguments(arguments)
                    .output(output)
                    .error(error)
                    .success(success)
                    .exitCode(exitCode)
                    .executionTimeMs(executionTimeMs)
                    .artifactPaths(artifactPaths)
                    .artifactOutputDirectory(artifactOutputDirectory)
                    .buildLog(buildLog)
                    .build();

            buildHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to save build history", e);
        }
    }

    public void saveDeployHistory(String registrationId, String deployId, String deployCommand, String arguments,
                                  String output, String error, boolean success, int exitCode, int executionTimeMs,
                                  String sessionId, String deployLog, String healthCheckStatus,
                                  Integer healthCheckResponseTime, boolean isRunning, String deploymentUrl) {
        try {
            CodeDeployHistory history = CodeDeployHistory.builder()
                    .registrationId(registrationId)
                    .sessionId(sessionId)
                    .deployId(deployId)
                    .deployCommand(deployCommand)
                    .arguments(arguments)
                    .output(output)
                    .error(error)
                    .success(success)
                    .exitCode(exitCode)
                    .executionTimeMs(executionTimeMs)
                    .deployLog(deployLog)
                    .healthCheckStatus(healthCheckStatus)
                    .healthCheckResponseTimeMs(healthCheckResponseTime)
                    .isRunning(isRunning)
                    .deploymentUrl(deploymentUrl)
                    .build();

            deployHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to save deploy history", e);
        }
    }
}
