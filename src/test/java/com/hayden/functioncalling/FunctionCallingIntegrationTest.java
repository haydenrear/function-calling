package com.hayden.functioncalling;

import com.hayden.commitdiffmodel.codegen.types.CodeExecutionOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionRegistration;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionRegistrationIn;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionResult;
import com.hayden.functioncalling.controller.CodeRunnerController;
import com.hayden.functioncalling.entity.CodeExecutionEntity;
import com.hayden.functioncalling.entity.CodeExecutionHistory;
import com.hayden.functioncalling.repository.CodeExecutionHistoryRepository;
import com.hayden.functioncalling.repository.CodeExecutionRepository;
import com.hayden.functioncalling.runner.ExecRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
public class FunctionCallingIntegrationTest {

    @Autowired
    private CodeRunnerController controller;
    
    @Autowired
    private CodeExecutionRepository executionRepository;
    
    @Autowired
    private CodeExecutionHistoryRepository historyRepository;
    
    @Autowired
    private ExecRunner execRunner;
    
    private Path tempOutputFile;
    
    @AfterEach
    void cleanup() {
        // Clean up any created temporary files
        if (tempOutputFile != null) {
            try {
                Files.deleteIfExists(tempOutputFile);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    @Transactional
    public void testCompleteExecutionFlow() throws Exception {
        // 1. Register a command
        String registrationId = UUID.randomUUID().toString();
        CodeExecutionRegistrationIn registrationIn = CodeExecutionRegistrationIn.newBuilder()
                .registrationId(registrationId)
                .command("echo")
                .arguments("Integration Test")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .description("Integration test command")
                .timeoutSeconds(10)
                .build();
                
        CodeExecutionRegistration registration = controller.registerCodeExecution(registrationIn);
        assertThat(registration).isNotNull();
        assertThat(registration.getRegistrationId()).isEqualTo(registrationId);
        
        // 2. Verify the registration was saved
        assertThat(executionRepository.findByRegistrationId(registrationId)).isPresent();
        
        // 3. Execute the command
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();
                
        CodeExecutionResult result = controller.execute(options);
        
        // 4. Verify the execution result
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Integration Test");
        assertThat(result.getExecutionId()).isNotNull();
        
        // 5. Verify the execution was logged in history
        String executionId = result.getExecutionId();
        assertThat(historyRepository.findByExecutionId(executionId)).isPresent();
        
        // 6. Get execution output by ID
        CodeExecutionResult retrievedResult = controller.getExecutionOutput(executionId);
        assertThat(retrievedResult).isNotNull();
        assertThat(retrievedResult.getOutput()).isEqualTo(result.getOutput());
        
        // 7. Execute with output to file
        tempOutputFile = Path.of(System.getProperty("java.io.tmpdir"), "integration_test_" + UUID.randomUUID() + ".log");
        CodeExecutionResult fileResult = controller.executeWithOutputFile(options, tempOutputFile.toString());
        
        // 8. Verify file output
        assertThat(fileResult).isNotNull();
        assertThat(fileResult.getSuccess()).isTrue();
        assertThat(fileResult.getOutputFile()).isEqualTo(tempOutputFile.toString());
        assertThat(Files.exists(tempOutputFile)).isTrue();
        String fileContent = Files.readString(tempOutputFile);
        assertThat(fileContent).contains("Integration Test");
        
        // 9. Update the registration
        CodeExecutionRegistration updatedReg = controller.updateCodeExecutionRegistration(
                registrationId,
                null,
                "cat",
                null,
                null,
                5);
                
        assertThat(updatedReg).isNotNull();
        assertThat(updatedReg.getCommand()).isEqualTo("cat");
        
        // 10. Retrieve all registrations
        List<CodeExecutionRegistration> allRegistrations = controller.retrieveRegistrations();
        assertThat(allRegistrations).isNotEmpty();
        assertThat(allRegistrations).anyMatch(reg -> reg.getRegistrationId().equals(registrationId));
        
        // 11. Retrieve execution history
        List<com.hayden.commitdiffmodel.codegen.types.CodeExecution> executions = controller.retrieveExecutions();
        assertThat(executions).isNotEmpty();
        
        // 12. Delete the registration
        Boolean deleteResult = controller.deleteCodeExecutionRegistration(registrationId);
        assertThat(deleteResult).isTrue();
        
        // 13. Verify it's gone
        assertThat(executionRepository.findByRegistrationId(registrationId)).isEmpty();
    }
    
    @Test
    @Transactional
    public void testFailedExecution() {
        // 1. Register a command that will fail
        String registrationId = UUID.randomUUID().toString();
        CodeExecutionRegistrationIn registrationIn = CodeExecutionRegistrationIn.newBuilder()
                .registrationId(registrationId)
                .command("ls")
                .arguments("/nonexistent_directory_" + UUID.randomUUID())
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .description("Command that will fail")
                .timeoutSeconds(10)
                .build();
                
        controller.registerCodeExecution(registrationIn);
        
        // 2. Execute the command
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();
                
        CodeExecutionResult result = controller.execute(options);
        
        // 3. Verify the execution failed
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getExitCode()).isNotEqualTo(0);
        assertThat(result.getExecutionId()).isNotNull();
        
        // 4. Check the history was recorded
        String executionId = result.getExecutionId();
        CodeExecutionHistory history = historyRepository.findByExecutionId(executionId).orElse(null);
        assertThat(history).isNotNull();
        assertThat(history.getSuccess()).isFalse();
    }
}