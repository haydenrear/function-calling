package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeExecutionEntity;
import com.hayden.functioncalling.entity.CodeExecutionHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Transactional
public class JpaRepositoryIntegrationTest {

    @Autowired
    private CodeExecutionRepository executionRepository;

    @Autowired
    private CodeExecutionHistoryRepository historyRepository;

    private String registrationId;

    @BeforeEach
    void setUp() {
        registrationId = UUID.randomUUID().toString();
    }

    @Test
    void testCodeExecutionEntityPersistence() {
        // Create and save an entity
        CodeExecutionEntity entity = CodeExecutionEntity.builder()
                .registrationId(registrationId)
                .command("test-command")
                .workingDirectory("/tmp")
                .description("Test description")
                .arguments("-a -b")
                .timeoutSeconds(30)
                .enabled(true)
                .build();

        CodeExecutionEntity saved = executionRepository.save(entity);

        // Check ID was generated
        assertThat(saved.getRegistrationId()).isNotNull();

        // Retrieve and verify
        CodeExecutionEntity retrieved = executionRepository.findByRegistrationId(saved.getRegistrationId()).orElseThrow();
        assertThat(retrieved.getRegistrationId()).isEqualTo(registrationId);
        assertThat(retrieved.getCommand()).isEqualTo("test-command");
        assertThat(retrieved.getWorkingDirectory()).isEqualTo("/tmp");
        assertThat(retrieved.getDescription()).isEqualTo("Test description");
        assertThat(retrieved.getArguments()).isEqualTo("-a -b");
        assertThat(retrieved.getTimeoutSeconds()).isEqualTo(30);
        assertThat(retrieved.getEnabled()).isTrue();

        // Verify audit fields
        assertThat(retrieved.getCreatedTime()).isNotNull();
        assertThat(retrieved.getUpdatedTime()).isNotNull();
    }

    @Test
    void testCodeExecutionEntityUniqueConstraint() {
        // Create and save an entity
        CodeExecutionEntity entity1 = CodeExecutionEntity.builder()
                .registrationId(registrationId)
                .command("test-command")
                .workingDirectory("/tmp")
                .enabled(true)
                .build();

        executionRepository.save(entity1);

        // Try to save another with the same registration ID
        CodeExecutionEntity entity2 = CodeExecutionEntity.builder()
                .registrationId(registrationId)  // Same ID
                .command("another-command")
                .enabled(true)
                .build();

        // This should throw an exception due to unique constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            executionRepository.save(entity2);
            executionRepository.flush(); // Force the flush to trigger constraint violation
        });
    }

    @Test
    void testCodeExecutionHistoryPersistence() {
        // Create and save a history entry
        String executionId = UUID.randomUUID().toString();
        CodeExecutionHistory history = CodeExecutionHistory.builder()
                .executionId(executionId)
                .registrationId(registrationId)
                .command("test-command")
                .arguments("-a -b")
                .success(true)
                .exitCode(0)
                .output("Command output")
                .executionTimeMs(150)
                .build();

        CodeExecutionHistory saved = historyRepository.save(history);

        // Check ID was generated
        assertThat(saved.getRegistrationId()).isNotNull();

        // Retrieve and verify
        CodeExecutionHistory retrieved = historyRepository.findByRegistrationId(saved.getRegistrationId()).getFirst();
        assertThat(retrieved.getExecutionId()).isEqualTo(executionId);
        assertThat(retrieved.getRegistrationId()).isEqualTo(registrationId);
        assertThat(retrieved.getCommand()).isEqualTo("test-command");
        assertThat(retrieved.getArguments()).isEqualTo("-a -b");
        assertThat(retrieved.getSuccess()).isTrue();
        assertThat(retrieved.getExitCode()).isEqualTo(0);
        assertThat(retrieved.getOutput()).isEqualTo("Command output");
        assertThat(retrieved.getExecutionTimeMs()).isEqualTo(150);

        // Verify audit fields
        assertThat(retrieved.getCreatedTime()).isNotNull();
        assertThat(retrieved.getUpdatedTime()).isNotNull();
    }

    @Test
    void testFindTop10ByOrderByExecutedAtDesc() {
        // Create 15 history entries with varying execution times
        IntStream.range(0, 15).forEach(i -> {
            CodeExecutionHistory history = CodeExecutionHistory.builder()
                    .executionId(UUID.randomUUID().toString())
                    .registrationId(registrationId)
                    .command("command-" + i)
                    .success(true)
                    .exitCode(0)
                    .executionTimeMs(100 + i)
                    .build();
            
            historyRepository.save(history);
        });

        // Find the top 10
        List<CodeExecutionHistory> top10 = historyRepository.findTop10ByOrderByCreatedTimeDesc();
        
        // Verify we got at most 10 results
        assertThat(top10.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void testFindByRegistrationIdInHistory() {
        // Create multiple history entries for the same registration ID
        String regId1 = UUID.randomUUID().toString();
        String regId2 = UUID.randomUUID().toString();
        
        // Create 5 histories for regId1
        IntStream.range(0, 5).forEach(i -> {
            CodeExecutionHistory history = CodeExecutionHistory.builder()
                    .executionId(UUID.randomUUID().toString())
                    .registrationId(regId1)
                    .command("command-" + i)
                    .success(true)
                    .exitCode(0)
                    .build();
            
            historyRepository.save(history);
        });
        
        // Create 3 histories for regId2
        IntStream.range(0, 3).forEach(i -> {
            CodeExecutionHistory history = CodeExecutionHistory.builder()
                    .executionId(UUID.randomUUID().toString())
                    .registrationId(regId2)
                    .command("other-command-" + i)
                    .success(true)
                    .exitCode(0)
                    .build();
            
            historyRepository.save(history);
        });

        // Find by first registration ID
        List<CodeExecutionHistory> reg1Histories = historyRepository.findByRegistrationId(regId1);
        assertThat(reg1Histories.size()).isEqualTo(5);
        assertThat(reg1Histories).allMatch(h -> h.getRegistrationId().equals(regId1));
        
        // Find by second registration ID
        List<CodeExecutionHistory> reg2Histories = historyRepository.findByRegistrationId(regId2);
        assertThat(reg2Histories.size()).isEqualTo(3);
        assertThat(reg2Histories).allMatch(h -> h.getRegistrationId().equals(regId2));
    }
}