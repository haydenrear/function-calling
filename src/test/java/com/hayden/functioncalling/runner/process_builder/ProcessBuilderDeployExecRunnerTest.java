package com.hayden.functioncalling.runner.process_builder;

import com.hayden.commitdiffmodel.codegen.types.CodeDeployOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeDeployResult;
import com.hayden.functioncalling.entity.CodeDeployEntity;
import com.hayden.functioncalling.repository.CodeDeployHistoryRepository;
import com.hayden.functioncalling.repository.CodeDeployRepository;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderDataService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
public class ProcessBuilderDeployExecRunnerTest {

    @Autowired
    private ProcessBuilderDeployExecRunner deployExecRunner;

    @Autowired
    private CodeDeployRepository deployRepository;

    @Autowired
    private CodeDeployHistoryRepository deployHistoryRepository;

    @Autowired
    private ProcessBuilderDataService deployDataService;

    private String successRegistrationId;
    private String noHealthRegistrationId;
    private String timeoutRegistrationId;
    private String disabledRegistrationId;

    @BeforeEach
    void setUp() {
        // Create test deploy registrations
        successRegistrationId = "test-deploy-success-" + UUID.randomUUID();
        noHealthRegistrationId = "test-deploy-no-health-" + UUID.randomUUID();
        timeoutRegistrationId = "test-deploy-timeout-" + UUID.randomUUID();
        disabledRegistrationId = "test-deploy-disabled-" + UUID.randomUUID();

        // Create successful deploy entity with health check
        CodeDeployEntity successEntity = CodeDeployEntity.builder()
                .registrationId(successRegistrationId)
                .deployCommand("bash")
                .arguments("src/test/resources/scripts/deploy.sh")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(30)
                .description("Test successful deploy")
                .deploySuccessPatterns(List.of("Deployment completed successfully!"))
                .deployFailurePatterns(List.of("Deployment failed!"))
                .healthCheckUrl("http://httpbin.org/status/200")
                .healthCheckTimeoutSeconds(5)
                .maxWaitForStartupSeconds(10)
                .stopCommand("bash src/test/resources/scripts/stop-deploy.sh")
                .build();

        // Create deploy entity without health check
        CodeDeployEntity noHealthEntity = CodeDeployEntity.builder()
                .registrationId(noHealthRegistrationId)
                .deployCommand("bash")
                .arguments("src/test/resources/scripts/deploy.sh")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(30)
                .description("Test deploy without health check")
                .deploySuccessPatterns(List.of("Deployment completed successfully!"))
                .deployFailurePatterns(List.of("Deployment failed!"))
                .stopCommand("bash src/test/resources/scripts/stop-deploy.sh")
                .build();

        // Create timeout deploy entity
        CodeDeployEntity timeoutEntity = CodeDeployEntity.builder()
                .registrationId(timeoutRegistrationId)
                .deployCommand("sleep")
                .arguments("60")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(2)
                .description("Test timeout deploy")
                .maxWaitForStartupSeconds(1)
                .build();

        // Create disabled deploy entity
        CodeDeployEntity disabledEntity = CodeDeployEntity.builder()
                .registrationId(disabledRegistrationId)
                .deployCommand("echo")
                .arguments("This should not run")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(false)
                .timeoutSeconds(30)
                .description("Test disabled deploy")
                .build();

        deployRepository.saveAll(List.of(successEntity, noHealthEntity, timeoutEntity, disabledEntity));
    }

    @Test
    void testDeploySuccessfulCommand() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(successRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeployLog()).contains("Deployment completed successfully!");
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getDeployId()).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(successRegistrationId);
        assertThat(result.getExecutionTime()).isGreaterThan(0);
        assertThat(result.getHealthCheckStatus()).isEqualTo("HEALTHY");
        assertThat(result.getHealthCheckResponseTime()).isGreaterThan(0);
        assertThat(result.getDeploymentUrl()).isEqualTo("http://httpbin.org/status/200");

        // Verify deploy history was saved
        assertThat(deployHistoryRepository.findByDeployId(result.getDeployId())).isPresent();
    }

    @Test
    void testDeployWithoutHealthCheck() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(noHealthRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeployLog()).contains("Deployment completed successfully!");
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getDeployId()).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(noHealthRegistrationId);
        assertThat(result.getHealthCheckStatus()).isNull();
        assertThat(result.getDeploymentUrl()).isNull();

        // Verify deploy history was saved
        assertThat(deployHistoryRepository.findByDeployId(result.getDeployId())).isPresent();
    }

    @Test
    void testDeployWithTimeout() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(timeoutRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        long startTime = System.currentTimeMillis();
        CodeDeployResult result = deployExecRunner.deploy(options);
        long endTime = System.currentTimeMillis();

        // Should not take more than ~5 seconds (giving some buffer for processing)
        assertTrue((endTime - startTime) < 10000);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getDeployId()).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(timeoutRegistrationId);

        // Error should mention timeout
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("timed out");
    }

    @Test
    void testDeployWithInvalidRegistrationId() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId("non-existent-id")
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("No code deploy registration found with ID");
    }

    @Test
    void testDeployWithDisabledRegistration() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(disabledRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("Code deploy registration is disabled");
    }

    @Test
    void testDeployWithNullRegistrationId() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .sessionId("test-session-" + UUID.randomUUID())
                .build();  // No registration ID

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("Registration ID is required");
    }

    @Test
    void testDeployWithCustomArguments() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(noHealthRegistrationId)
                .arguments("src/test/resources/scripts/deploy.sh custom-arg")
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeployId()).isNotNull();
    }

    @Test
    void testDeployWithCustomTimeout() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(noHealthRegistrationId)
                .timeoutSeconds(60)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeployId()).isNotNull();
    }

    @Test
    void testDeployAsync() throws Exception {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(noHealthRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CompletableFuture<CodeDeployResult> future = deployExecRunner.deployAsync(options);

        assertThat(future).isNotNull();
        CodeDeployResult result = future.get();

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeployId()).isNotNull();
    }

    @Test
    void testDeployHistoryPersistence() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(noHealthRegistrationId)
                .sessionId("test-session-persistence")
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();

        // Verify history was persisted with correct details
        var historyOpt = deployHistoryRepository.findByDeployId(result.getDeployId());
        assertThat(historyOpt).isPresent();

        var history = historyOpt.get();
        assertThat(history.getRegistrationId()).isEqualTo(noHealthRegistrationId);
        assertThat(history.getSessionId()).isEqualTo("test-session-persistence");
        assertThat(history.getDeployCommand()).isEqualTo("bash");
        assertThat(history.getSuccess()).isTrue();
        assertThat(history.getExitCode()).isEqualTo(0);
        assertThat(history.getExecutionTimeMs()).isGreaterThan(0);
    }

    @Test
    void testDeployWithPatternMatching() {
        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(noHealthRegistrationId)
                .sessionId("test-session-patterns")
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();

        // Verify the success pattern was matched
        assertThat(result.getDeployLog()).contains("Deployment completed successfully!");
        assertThat(result.getDeployLog()).contains("Stage 1: Preparing deployment environment");
        assertThat(result.getDeployLog()).contains("Stage 2: Deploying application");
        assertThat(result.getDeployLog()).contains("Stage 3: Running health checks");
        assertThat(result.getDeployLog()).contains("Stage 4: Finalizing deployment");
    }

    @Test
    void testStopDeployment() {
        // First create a deployment entity with stop command
        String registrationId = "test-stop-deploy-" + UUID.randomUUID();
        CodeDeployEntity entity = CodeDeployEntity.builder()
                .registrationId(registrationId)
                .deployCommand("echo")
                .arguments("Fake deploy")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(30)
                .description("Test deploy with stop")
                .stopCommand("bash src/test/resources/scripts/stop-deploy.sh")
                .build();

        deployRepository.save(entity);

        String sessionId = "test-session-stop-" + UUID.randomUUID();
        CodeDeployResult result = deployExecRunner.stopDeployment(registrationId, sessionId);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getDeployLog()).contains("Deployment stopped successfully!");
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getDeployId()).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(registrationId);
        assertThat(result.getHealthCheckStatus()).isEqualTo("STOPPED");
        assertThat(result.getIsRunning()).isFalse();

        // Verify stop history was saved
        assertThat(deployHistoryRepository.findByDeployId(result.getDeployId())).isPresent();
    }

    @Test
    void testStopDeploymentWithoutStopCommand() {
        // Create a deployment entity without stop command
        String registrationId = "test-no-stop-deploy-" + UUID.randomUUID();
        CodeDeployEntity entity = CodeDeployEntity.builder()
                .registrationId(registrationId)
                .deployCommand("echo")
                .arguments("Fake deploy")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(30)
                .description("Test deploy without stop")
                .build();

        deployRepository.save(entity);

        String sessionId = "test-session-no-stop-" + UUID.randomUUID();
        CodeDeployResult result = deployExecRunner.stopDeployment(registrationId, sessionId);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("No stop command configured");
    }

    @Test
    void testStopDeploymentWithInvalidRegistrationId() {
        String sessionId = "test-session-invalid-" + UUID.randomUUID();
        CodeDeployResult result = deployExecRunner.stopDeployment("non-existent-id", sessionId);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("No code deploy registration found with ID");
    }

    @Test
    void testDeployWithFailedHealthCheck() {
        // Create entity with health check URL that will fail
        String registrationId = "test-deploy-bad-health-" + UUID.randomUUID();
        CodeDeployEntity entity = CodeDeployEntity.builder()
                .registrationId(registrationId)
                .deployCommand("bash")
                .arguments("src/test/resources/scripts/deploy.sh")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(30)
                .description("Test deploy with bad health check")
                .deploySuccessPatterns(List.of("Deployment completed successfully!"))
                .deployFailurePatterns(List.of("Deployment failed!"))
                .healthCheckUrl("http://httpbin.org/status/500")
                .healthCheckTimeoutSeconds(5)
                .maxWaitForStartupSeconds(10)
                .build();

        deployRepository.save(entity);

        CodeDeployOptions options = CodeDeployOptions.newBuilder()
                .registrationId(registrationId)
                .sessionId("test-session-bad-health")
                .build();

        CodeDeployResult result = deployExecRunner.deploy(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getHealthCheckStatus()).startsWith("UNHEALTHY");
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("Health check failed");
    }
}
