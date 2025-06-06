package com.hayden.functioncalling.service;

import com.hayden.functioncalling.entity.CodeExecutionHistory;
import com.hayden.functioncalling.repository.CodeExecutionHistoryRepository;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderExecutionDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class ExecutionDataServiceTest {

    @Mock
    private CodeExecutionHistoryRepository executionHistoryRepository;

    @InjectMocks
    private ProcessBuilderExecutionDataService executionDataService;

    @Captor
    private ArgumentCaptor<CodeExecutionHistory> historyCaptor;

    @Test
    void testSaveExecutionHistory_Success() {
        // Given
        String executionId = "test-exec-id";
        String command = "echo";
        String arguments = "Hello World";
        String output = "Hello World\n";
        String error = null;
        boolean success = true;
        int exitCode = 0;
        int executionTimeMs = 100;

        // When
        executionDataService.saveExecutionHistory(
                "test-exec-id-1",
                executionId,
                command,
                arguments,
                output,
                error,
                success,
                exitCode,
                executionTimeMs,
                "test_session");

        // Then
        verify(executionHistoryRepository, times(1)).save(historyCaptor.capture());

        CodeExecutionHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getExecutionId()).isEqualTo(executionId);
        assertThat(savedHistory.getCommand()).isEqualTo(command);
        assertThat(savedHistory.getArguments()).isEqualTo(arguments);
        assertThat(savedHistory.getOutput()).isEqualTo(output);
        assertThat(savedHistory.getError()).isEqualTo(error);
        assertThat(savedHistory.getSuccess()).isEqualTo(success);
        assertThat(savedHistory.getExitCode()).isEqualTo(exitCode);
        assertThat(savedHistory.getExecutionTimeMs()).isEqualTo(executionTimeMs);
    }

    @Test
    void testSaveExecutionHistory_Failure() {
        // Given
        String executionId = "test-exec-id";
        String command = "ls";
        String arguments = "/nonexistent";
        String output = "";
        String error = "No such file or directory";
        boolean success = false;
        int exitCode = 1;
        int executionTimeMs = 50;

        // When
        executionDataService.saveExecutionHistory(
                "test-exec-id-2",
                executionId,
                command,
                arguments,
                output,
                error,
                success,
                exitCode,
                executionTimeMs,
                "test_session");

        // Then
        verify(executionHistoryRepository, times(1)).save(historyCaptor.capture());

        CodeExecutionHistory savedHistory = historyCaptor.getValue();
        assertThat(savedHistory.getExecutionId()).isEqualTo(executionId);
        assertThat(savedHistory.getCommand()).isEqualTo(command);
        assertThat(savedHistory.getArguments()).isEqualTo(arguments);
        assertThat(savedHistory.getOutput()).isEqualTo(output);
        assertThat(savedHistory.getError()).isEqualTo(error);
        assertThat(savedHistory.getSuccess()).isEqualTo(success);
        assertThat(savedHistory.getExitCode()).isEqualTo(exitCode);
        assertThat(savedHistory.getExecutionTimeMs()).isEqualTo(executionTimeMs);
    }

    @Test
    void testSaveExecutionHistory_HandlesException() {
        // Given
        String executionId = "test-exec-id";
        
        doThrow(new RuntimeException("Database error")).when(executionHistoryRepository).save(any());

        // When - this should not throw an exception
        executionDataService.saveExecutionHistory(
                "test-exec-id-3",
                executionId,
                "echo",
                "test",
                "test output",
                null,
                true,
                0,
                100,
                "test_session");

        // Then - verify the method handled the exception
        verify(executionHistoryRepository, times(1)).save(any());
    }
}