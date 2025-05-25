package com.hayden.functioncalling.runner.process_builder_docker;

import com.hayden.commitdiffmodel.codegen.types.CodeExecutionOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionResult;
import com.hayden.functioncalling.entity.CodeExecutionEntity;
import com.hayden.functioncalling.repository.CodeExecutionHistoryRepository;
import com.hayden.functioncalling.repository.CodeExecutionRepository;
import com.hayden.functioncalling.service.ExecutionDataService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
public class ProcessBuilderExecRunnerTest {

    @Autowired
    private ProcessBuilderExecRunner execRunner;

    @Autowired
    private CodeExecutionRepository executionRepository;

    @Autowired
    private CodeExecutionHistoryRepository historyRepository;

    @Autowired
    private ExecutionDataService executionDataService;

    private String registrationId;

    @BeforeEach
    void setUp() {
        // Create a test code execution registration
        registrationId = UUID.randomUUID().toString();
        CodeExecutionEntity entity = CodeExecutionEntity.builder()
                .registrationId(registrationId)
                .command("echo")
                .arguments("Hello World")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(10)
                .description("Test command")
                .build();

        executionRepository.save(entity);
    }

    @Test
    void testRunSuccessfulCommand() {
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();

        CodeExecutionResult result = execRunner.run(options);

        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Hello World");
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getExecutionId()).isNotNull();

        // Verify history was saved
        assertThat(historyRepository.findByExecutionId(result.getExecutionId())).isPresent();
    }

    @Test
    void testRunWithInvalidRegistrationId() {
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId("non-existent-id")
                .build();

        CodeExecutionResult result = execRunner.run(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).contains("No code execution registration found with ID");
    }

    @SneakyThrows
    @Test
    void testRunWithDisabledRegistration() {
        // Disable the registration
        Optional<CodeExecutionEntity> entityOpt = executionRepository.findByRegistrationId(registrationId);
        assertTrue(entityOpt.isPresent());
        
        CodeExecutionEntity entity = entityOpt.get();
        entity.setEnabled(false);
        executionRepository.save(entity);

        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();

        CodeExecutionResult result = execRunner.run(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).contains("Code execution registration is disabled");

    }

    @Test
    void testRunWithCustomArguments() {
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .arguments("Custom Arguments")
                .build();

        CodeExecutionResult result = execRunner.run(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Custom Arguments");
    }

    @Test
    void testRunWithOutputFile() throws Exception {
        String tempFilePath = System.getProperty("java.io.tmpdir") + "/test_output_" + UUID.randomUUID() + ".txt";

        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .writeToFile(true)
                .outputFilePath(tempFilePath)
                .build();

        CodeExecutionResult result = execRunner.run(options);

        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOutputFile()).isEqualTo(tempFilePath);
        
        // Verify the file exists and contains output
        File outputFile = new File(tempFilePath);
        assertTrue(outputFile.exists());
        String fileContent = Files.readString(Paths.get(tempFilePath));
        assertThat(fileContent).contains("Hello World");
        
        // Clean up
        outputFile.delete();
    }

    @Test
    void testRunWithTimeout() {
        // First modify the registration to use a long-running command
        Optional<CodeExecutionEntity> entityOpt = executionRepository.findByRegistrationId(registrationId);
        assertTrue(entityOpt.isPresent());
        
        CodeExecutionEntity entity = entityOpt.get();
        entity.setCommand("sleep");
        entity.setArguments("10");  // Sleep for 10 seconds
        entity.setTimeoutSeconds(1); // But timeout after 1 second
        executionRepository.save(entity);

        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();

        long startTime = System.currentTimeMillis();
        CodeExecutionResult result = execRunner.run(options);
        long endTime = System.currentTimeMillis();

        // Should not take more than ~2 seconds (giving some buffer for processing)
        assertTrue((endTime - startTime) < 5000);
        
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).contains("timed out");
    }

    @Test
    void testRunNonZeroExitCode() {
        // Modify the registration to use a command that will fail
        Optional<CodeExecutionEntity> entityOpt = executionRepository.findByRegistrationId(registrationId);
        assertTrue(entityOpt.isPresent());
        
        CodeExecutionEntity entity = entityOpt.get();
        entity.setCommand("ls");
        entity.setArguments("/nonexistentdirectory");  // This should fail with exit code 1 or 2
        executionRepository.save(entity);

        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();

        CodeExecutionResult result = execRunner.run(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getExitCode()).isNotEqualTo(0);
    }

    @Test
    void testRunWithNullRegistrationId() {
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .build();  // No registration ID

        CodeExecutionResult result = execRunner.run(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).contains("Registration ID is required");
    }
}