package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.TestExecutionHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")

public class TestExecutionHistoryRepositoryTest {

    @Autowired
    private TestExecutionHistoryRepository repository;

    private String executionId;
    private String registrationId;

    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID().toString();
        registrationId = UUID.randomUUID().toString();

        // Create and save a test execution history
        TestExecutionHistory history = TestExecutionHistory.builder()
                .executionId(executionId)
                .registrationId(registrationId)
                .command("echo")
                .arguments("Hello World")
                .success(true)
                .exitCode(0)
                .output("Hello World\n")
                .executionTimeMs(100)
                .build();

        repository.save(history);

        // Add more test data for finding recent executions
        for (int i = 0; i < 15; i++) {
            TestExecutionHistory additionalHistory = TestExecutionHistory.builder()
                    .executionId(UUID.randomUUID().toString())
                    .registrationId(registrationId)
                    .command("test-command-" + i)
                    .arguments("arg-" + i)
                    .success(i % 2 == 0) // Alternate success/failure
                    .exitCode(i % 2 == 0 ? 0 : 1)
                    .output("Output " + i)
                    .error(i % 2 == 0 ? null : "Error " + i)
                    .executionTimeMs(100 + i)
                    .build();
            
            repository.save(additionalHistory);
        }
    }

    @Test
    void testFindByExecutionId() {
        Optional<TestExecutionHistory> found = repository.findByExecutionId(executionId);
        
        assertThat(found).isPresent();
        assertThat(found.get().getExecutionId()).isEqualTo(executionId);
        assertThat(found.get().getCommand()).isEqualTo("echo");
        assertThat(found.get().getArguments()).isEqualTo("Hello World");
        assertThat(found.get().getSuccess()).isTrue();
        assertThat(found.get().getExitCode()).isEqualTo(0);
        assertThat(found.get().getOutput()).isEqualTo("Hello World\n");
    }

    @Test
    void testFindByNonexistentExecutionId() {
        Optional<TestExecutionHistory> found = repository.findByExecutionId("nonexistent-id");
        assertThat(found).isEmpty();
    }

    @Test
    void testFindByRegistrationId() {
        List<TestExecutionHistory> histories = repository.findByRegistrationId(registrationId);
        
        assertThat(histories).isNotEmpty();
        assertThat(histories.size()).isEqualTo(16); // Our original plus the 15 added in setup
        assertThat(histories).allMatch(history -> history.getRegistrationId().equals(registrationId));
    }

    @Test
    void testFindTop10ByOrderByExecutedAtDesc() {
        List<TestExecutionHistory> recentExecutions = repository.findTop10ByOrderByCreatedTimeDesc();
        
        assertThat(recentExecutions).isNotEmpty();
        assertThat(recentExecutions.size()).isLessThanOrEqualTo(10);
        
        // Verify they are in descending order by executedAt
        if (recentExecutions.size() > 1) {
            for (int i = 0; i < recentExecutions.size() - 1; i++) {
                LocalDateTime current = recentExecutions.get(i).getCreatedTime();
                LocalDateTime next = recentExecutions.get(i + 1).getCreatedTime();
                
                // Skip if either is null (should not happen with proper entity setup)
                if (current != null && next != null) {
                    assertThat(current).isAfterOrEqualTo(next);
                }
            }
        }
    }

    @Test
    void testSaveAndDelete() {
        // Create a new history entry
        String newExecutionId = UUID.randomUUID().toString();
        TestExecutionHistory newHistory = TestExecutionHistory.builder()
                .executionId(newExecutionId)
                .registrationId(registrationId)
                .command("test-command")
                .arguments("test-args")
                .success(true)
                .exitCode(0)
                .output("Test output")
                .executionTimeMs(200)
                .build();

        // Save it
        TestExecutionHistory savedHistory = repository.save(newHistory);
        assertThat(savedHistory.getRegistrationId()).isNotNull();
        
        // Verify it can be found
        Optional<TestExecutionHistory> found = repository.findByExecutionId(newExecutionId);
        assertThat(found).isPresent();
        
        // Delete it
        repository.delete(savedHistory);
        
        // Verify it's gone
        assertThat(repository.findByExecutionId(newExecutionId)).isEmpty();
    }

    @Test
    void testFindAll() {
        List<TestExecutionHistory> allHistories = repository.findAll();
        assertThat(allHistories.size()).isGreaterThanOrEqualTo(16); // At least our test data
    }
}