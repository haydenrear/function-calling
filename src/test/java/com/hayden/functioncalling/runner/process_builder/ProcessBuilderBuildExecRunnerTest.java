package com.hayden.functioncalling.runner.process_builder;

import com.hayden.commitdiffmodel.codegen.types.CodeBuildOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeBuildResult;
import com.hayden.functioncalling.entity.CodeBuildEntity;
import com.hayden.functioncalling.repository.CodeBuildHistoryRepository;
import com.hayden.functioncalling.repository.CodeBuildRepository;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderDataService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
public class ProcessBuilderBuildExecRunnerTest {

    @Autowired
    private ProcessBuilderBuildExecRunner buildExecRunner;

    @Autowired
    private CodeBuildRepository buildRepository;

    @Autowired
    private CodeBuildHistoryRepository buildHistoryRepository;

    @Autowired
    private ProcessBuilderDataService buildDataService;

    private String successRegistrationId;
    private String failRegistrationId;
    private String timeoutRegistrationId;
    private String disabledRegistrationId;

    @BeforeEach
    void setUp() {
        // Create test build registrations
        successRegistrationId = "test-build-success-" + UUID.randomUUID();
        failRegistrationId = "test-build-fail-" + UUID.randomUUID();
        timeoutRegistrationId = "test-build-timeout-" + UUID.randomUUID();
        disabledRegistrationId = "test-build-disabled-" + UUID.randomUUID();

        // Create successful build entity
        CodeBuildEntity successEntity = CodeBuildEntity.builder()
                .registrationId(successRegistrationId)
                .buildCommand("bash")
                .arguments("src/test/resources/scripts/build.sh")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(30)
                .description("Test successful build")
                .artifactPaths(List.of("target/myapp-1.0.0.jar"))
                .artifactOutputDirectory(System.getProperty("java.io.tmpdir") + "/test-artifacts")
                .buildSuccessPatterns(List.of("Build completed successfully!"))
                .buildFailurePatterns(List.of("Build failed with errors!"))
                .build();

        // Create failing build entity
        CodeBuildEntity failEntity = CodeBuildEntity.builder()
                .registrationId(failRegistrationId)
                .buildCommand("bash")
                .arguments("src/test/resources/scripts/build-fail.sh")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(30)
                .description("Test failing build")
                .buildSuccessPatterns(List.of("Build completed successfully!"))
                .buildFailurePatterns(List.of("Build failed with errors!"))
                .build();

        // Create timeout build entity
        CodeBuildEntity timeoutEntity = CodeBuildEntity.builder()
                .registrationId(timeoutRegistrationId)
                .buildCommand("sleep")
                .arguments("60")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(2)
                .description("Test timeout build")
                .build();

        // Create disabled build entity
        CodeBuildEntity disabledEntity = CodeBuildEntity.builder()
                .registrationId(disabledRegistrationId)
                .buildCommand("echo")
                .arguments("This should not run")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(false)
                .timeoutSeconds(30)
                .description("Test disabled build")
                .build();

        buildRepository.saveAll(List.of(successEntity, failEntity, timeoutEntity, disabledEntity));
    }

    @Test
    void testBuildSuccessfulCommand() {
        // Create target directory for artifacts
        createTargetDirectory();

        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(successRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getBuildLog()).contains("Build completed successfully!");
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getBuildId()).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(successRegistrationId);
        assertThat(result.getExecutionTime()).isGreaterThan(0);

        // Verify build history was saved
        assertThat(buildHistoryRepository.findByBuildId(result.getBuildId())).isPresent();

        // Verify artifacts were copied (if target directory exists)
        if (Files.exists(Paths.get("target"))) {
            assertThat(result.getArtifactPaths()).isNotEmpty();
            assertThat(result.getArtifactOutputDirectory()).isNotNull();
        }
    }

    @Test
    void testBuildFailingCommand() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(failRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        // Verify the result
        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getBuildLog()).contains("Build failed with errors!");
        assertThat(result.getExitCode()).isEqualTo(1);
        assertThat(result.getBuildId()).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(failRegistrationId);

        // Verify build history was saved
        assertThat(buildHistoryRepository.findByBuildId(result.getBuildId())).isPresent();
    }

    @Test
    void testBuildWithTimeout() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(timeoutRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        long startTime = System.currentTimeMillis();
        CodeBuildResult result = buildExecRunner.build(options);
        long endTime = System.currentTimeMillis();

        // Should not take more than ~5 seconds (giving some buffer for processing)
        assertTrue((endTime - startTime) < 10000);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getBuildId()).isNotNull();
        assertThat(result.getRegistrationId()).isEqualTo(timeoutRegistrationId);

        // Error should mention timeout
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("timed out");
    }

    @Test
    void testBuildWithInvalidRegistrationId() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId("non-existent-id")
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("No code build registration found with ID");
    }

    @Test
    void testBuildWithDisabledRegistration() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(disabledRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("Code build registration is disabled");
    }

    @Test
    void testBuildWithNullRegistrationId() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .sessionId("test-session-" + UUID.randomUUID())
                .build();  // No registration ID

        CodeBuildResult result = buildExecRunner.build(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getError()).isNotEmpty();
        assertThat(result.getError().get(0).getMessage()).contains("Registration ID is required");
    }

    @Test
    void testBuildWithCustomArguments() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(successRegistrationId)
                .arguments("src/test/resources/scripts/build.sh custom-arg")
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getBuildId()).isNotNull();
    }

    @Test
    void testBuildWithCustomTimeout() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(successRegistrationId)
                .timeoutSeconds(60)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getBuildId()).isNotNull();
    }

    @Test
    void testBuildAsync() throws Exception {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(successRegistrationId)
                .sessionId("test-session-" + UUID.randomUUID())
                .build();

        CompletableFuture<CodeBuildResult> future = buildExecRunner.buildAsync(options);

        assertThat(future).isNotNull();
        CodeBuildResult result = future.get();

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getBuildId()).isNotNull();
    }

    @Test
    void testBuildHistoryPersistence() {
        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(successRegistrationId)
                .sessionId("test-session-persistence")
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();

        // Verify history was persisted with correct details
        var historyOpt = buildHistoryRepository.findByBuildId(result.getBuildId());
        assertThat(historyOpt).isPresent();

        var history = historyOpt.get();
        assertThat(history.getRegistrationId()).isEqualTo(successRegistrationId);
        assertThat(history.getSessionId()).isEqualTo("test-session-persistence");
        assertThat(history.getBuildCommand()).isEqualTo("bash");
        assertThat(history.getSuccess()).isTrue();
        assertThat(history.getExitCode()).isEqualTo(0);
        assertThat(history.getExecutionTimeMs()).isGreaterThan(0);
    }

    @Test
    void testBuildWithPatternMatching() {
        // Create target directory for artifacts
        createTargetDirectory();

        CodeBuildOptions options = CodeBuildOptions.newBuilder()
                .registrationId(successRegistrationId)
                .sessionId("test-session-patterns")
                .build();

        CodeBuildResult result = buildExecRunner.build(options);

        assertThat(result).isNotNull();
        assertThat(result.getSuccess()).isTrue();

        // Verify the success pattern was matched
        assertThat(result.getBuildLog()).contains("Build completed successfully!");
        assertThat(result.getBuildLog()).contains("Stage 1: Compiling source code");
        assertThat(result.getBuildLog()).contains("Stage 2: Running tests");
        assertThat(result.getBuildLog()).contains("Stage 3: Packaging artifacts");
    }

    @SneakyThrows
    private void createTargetDirectory() {
        File targetDir = new File("target");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
    }
}
