package com.hayden.functioncalling.controller;

import com.hayden.commitdiffmodel.codegen.types.CodeExecution;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionRegistration;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionRegistrationIn;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionResult;
import com.hayden.functioncalling.entity.CodeExecutionEntity;
import com.hayden.functioncalling.entity.CodeExecutionHistory;
import com.hayden.functioncalling.repository.CodeExecutionHistoryRepository;
import com.hayden.functioncalling.repository.CodeExecutionRepository;
import com.hayden.functioncalling.runner.ExecRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Transactional
public class CodeRunnerControllerTest {

    @Autowired
    private CodeRunnerController controller;

    @Autowired
    private CodeExecutionRepository executionRepository;

    @Autowired
    private CodeExecutionHistoryRepository historyRepository;

    @Autowired
    private ExecRunner execRunner;

    private String registrationId;
    private String cdcAgentsTest;

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

        // Create some test execution history
        CodeExecutionHistory history = CodeExecutionHistory.builder()
                .executionId(UUID.randomUUID().toString())
                .registrationId(registrationId)
                .command("echo")
                .arguments("Hello World")
                .success(true)
                .exitCode(0)
                .output("Hello World\n")
                .executionTimeMs(100)
                .build();

        historyRepository.save(history);

        cdcAgentsTest = UUID.randomUUID().toString();
        CodeExecutionEntity runCdcAgentsDockerTest = CodeExecutionEntity.builder()
                .registrationId(cdcAgentsTest)
                .command("uv")
                .arguments("run test-cdc-agents")
                .workingDirectory("/Users/hayde/IdeaProjects/drools/python_parent/packages/cdc_agents")
                .enabled(true)
                .description("Test command")
                .build();

        executionRepository.save(runCdcAgentsDockerTest);

    }

//    @Test
//    void runCdcAgentsTest() {
//        var found = execRunner.run(CodeExecutionOptions.newBuilder()
//                        .registrationId(cdcAgentsTest).build());
//        System.out.println(found);
//    }

    @Test
    void testRetrieveRegistrations() {
        List<CodeExecutionRegistration> registrations = controller.retrieveRegistrations();
        assertThat(registrations).isNotEmpty();
        assertThat(registrations).anyMatch(reg -> reg.getRegistrationId().equals(registrationId));
    }

    @Test
    void testRetrieveExecutions() {
        List<CodeExecution> executions = controller.retrieveExecutions();
        assertThat(executions).isNotEmpty();
        assertThat(executions).anyMatch(exec -> exec.getCommand().contains("Hello World"));
    }

    @Test
    void testGetCodeExecutionRegistration() {
        CodeExecutionRegistration registration = controller.getCodeExecutionRegistration(registrationId);
        assertThat(registration).isNotNull();
        assertThat(registration.getRegistrationId()).isEqualTo(registrationId);
        assertThat(registration.getCommand()).isEqualTo("echo");
        assertThat(registration.getArguments()).isEqualTo("Hello World");
    }

    @Test
    void testRegisterCodeExecution() {
        String newRegId = UUID.randomUUID().toString();
        CodeExecutionRegistrationIn regIn = CodeExecutionRegistrationIn.newBuilder()
                .registrationId(newRegId)
                .command("ls")
                .arguments("-la")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .description("List files")
                .timeoutSeconds(5)
                .build();

        CodeExecutionRegistration result = controller.registerCodeExecution(regIn);
        
        assertThat(result).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(newRegId);
        assertThat(result.getCommand()).isEqualTo("ls");
        
        // Verify it was saved to the database
        assertThat(executionRepository.findByRegistrationId(newRegId)).isPresent();
    }

    @Test
    void testUpdateCodeExecutionRegistration() {
        CodeExecutionRegistration result = controller.updateCodeExecutionRegistration(
                registrationId, 
                null, 
                "ls",
                null, 
                "-la", 
                20,
                "test_session");
        
        assertThat(result).isNotNull();
        assertThat(result.getCommand()).isEqualTo("ls");
        assertThat(result.getArguments()).isEqualTo("-la");
        assertThat(result.getTimeoutSeconds()).isEqualTo(20);
        
        // Verify the database was updated
        CodeExecutionEntity entity = executionRepository.findByRegistrationId(registrationId).orElseThrow();
        assertThat(entity.getCommand()).isEqualTo("ls");
        assertThat(entity.getArguments()).isEqualTo("-la");
        assertThat(entity.getTimeoutSeconds()).isEqualTo(20);
    }

    @Test
    void testDeleteCodeExecutionRegistration() {
        Boolean result = controller.deleteCodeExecutionRegistration(registrationId,
                "test_session");
        
        assertThat(result).isTrue();
        assertThat(executionRepository.findByRegistrationId(registrationId)).isEmpty();
    }

    @Test
    void testExecute() {
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();
        
        CodeExecutionResult result = controller.execute(options);
        
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Hello World");
    }

    @Test
    void testExecuteWithOutputFile() {
        String outputPath = System.getProperty("java.io.tmpdir") + "/test_output_" + UUID.randomUUID() + ".txt";
        
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();
        
        CodeExecutionResult result = controller.executeWithOutputFile(options, outputPath);
        
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOutputFile()).isEqualTo(outputPath);
    }

    @Test
    void testGetExecutionOutput() {
        // First execute a command to get an execution ID
        CodeExecutionOptions options = CodeExecutionOptions.newBuilder()
                .registrationId(registrationId)
                .build();
        
        CodeExecutionResult executeResult = controller.execute(options);
        assertThat(executeResult).isNotNull();
        
        // Then get the output for that execution
        CodeExecutionResult result = controller.getExecutionOutput(executeResult.getExecutionId(),
                "test_session");
        
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getOutput()).contains("Hello World");
    }
}