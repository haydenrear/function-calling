package com.hayden.functioncalling.service;

import com.hayden.functioncalling.entity.CodeExecutionHistory;
import com.hayden.functioncalling.repository.CodeExecutionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionDataService {

    private final CodeExecutionHistoryRepository executionHistoryRepository;

    @Transactional
    public void saveExecutionHistory(String registrationId, String executionId, String command, String arguments,
                                     String output, String error, boolean success, int exitCode, int executionTimeMs, String sessionId) {
        try {
            CodeExecutionHistory history = CodeExecutionHistory.builder()
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
}
