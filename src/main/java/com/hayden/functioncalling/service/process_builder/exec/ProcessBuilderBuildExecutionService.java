package com.hayden.functioncalling.service.process_builder.exec;

import com.hayden.commitdiffmodel.codegen.types.CodeBuildOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeBuildResult;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.functioncalling.entity.CodeBuildEntity;
import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import com.hayden.functioncalling.service.ExecutionService;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderDataService;
import com.hayden.functioncalling.service.process_builder.ProcessExecutionRequest;
import com.hayden.functioncalling.service.process_builder.ProcessExecutionResult;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderBuildExecutionService implements ExecutionService<CodeBuildEntity, CodeBuildResult, CodeBuildOptions> {

    private final ProcessBuilderExecutionService processBuilderService;
    private final ProcessBuilderDataService buildDataService;

    @Override
    public CodeBuildResult execute(CodeBuildEntity entity, CodeBuildOptions options) {
        try {
            return executeBuild(entity, options);
        } catch (Exception e) {
            log.error("Error executing build command", e);
            return CodeBuildResult.newBuilder()
                    .success(false)
                    .registrationId(options.getRegistrationId())
                    .error(List.of(new Error("Error executing build command: " + e.getMessage())))
                    .build();
        }
    }

    @Override
    public ExecutionType getExecutionType() {
        return ExecutionType.PROCESS_BUILDER;
    }

    private CodeBuildResult executeBuild(CodeBuildEntity entity, CodeBuildOptions options) throws Exception {
        String buildId = UUID.randomUUID().toString();

        // Determine arguments
        String arguments = null;
        if (StringUtils.isNotBlank(options.getArguments())) {
            arguments = options.getArguments();
        } else if (StringUtils.isNotBlank(entity.getArguments())) {
            arguments = entity.getArguments();
        }

        // Determine timeout
        Integer timeoutSeconds = options.getTimeoutSeconds() != null
                ? options.getTimeoutSeconds()
                : entity.getTimeoutSeconds() != null
                ? entity.getTimeoutSeconds()
                : null;

        // Build process execution request
        ProcessExecutionRequest request = ProcessExecutionRequest.builder()
                .command(entity.getBuildCommand())
                .arguments(arguments)
                .workingDirectory(entity.getWorkingDirectory())
                .timeoutSeconds(timeoutSeconds)
                .outputRegex(entity.getOutputRegex())
                .successPatterns(entity.getBuildSuccessPatterns())
                .failurePatterns(entity.getBuildFailurePatterns())
                .build();

        // Execute using ProcessBuilderService
        ProcessExecutionResult result = processBuilderService.executeProcess(request);

        // Handle artifacts if build was successful
        List<String> copiedArtifacts = new ArrayList<>();
        if (result.isSuccess() && entity.getArtifactPaths() != null && !entity.getArtifactPaths().isEmpty()) {
            copiedArtifacts = copyArtifacts(entity, buildId);
        }

        // Save build history
        buildDataService.saveBuildHistory(
                entity.getRegistrationId(),
                buildId,
                entity.getBuildCommand(),
                arguments,
                result.getOutput(),
                result.getError(),
                result.isSuccess(),
                result.getExitCode(),
                result.getExecutionTimeMs(),
                options.getSessionId(),
                copiedArtifacts,
                entity.getArtifactOutputDirectory(),
                result.getFullLog()
        );

        return CodeBuildResult.newBuilder()
                .registrationId(options.getRegistrationId())
                .success(result.isSuccess())
                .output(result.getOutput())
                .sessionId(options.getSessionId())
                .exitCode(result.getExitCode())
                .executionTime(result.getExecutionTimeMs())
                .buildId(buildId)
                .error(List.of(new Error(result.getError())))
                .artifactPaths(copiedArtifacts)
                .artifactOutputDirectory(entity.getArtifactOutputDirectory())
                .buildLog(result.getFullLog())
                .build();
    }

    private List<String> copyArtifacts(CodeBuildEntity entity, String buildId) {
        List<String> copiedArtifacts = new ArrayList<>();

        if (entity.getArtifactOutputDirectory() == null) {
            log.warn("No artifact output directory specified, skipping artifact copy");
            return copiedArtifacts;
        }

        try {
            Path outputDir = Paths.get(entity.getArtifactOutputDirectory(), buildId);
            Files.createDirectories(outputDir);

            for (String artifactPath : entity.getArtifactPaths()) {
                Path sourcePath = Paths.get(artifactPath);

                if (entity.getWorkingDirectory() != null) {
                    sourcePath = Paths.get(entity.getWorkingDirectory()).resolve(artifactPath);
                }

                if (Files.exists(sourcePath)) {
                    Path targetPath = outputDir.resolve(sourcePath.getFileName());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    copiedArtifacts.add(targetPath.toString());
                    log.info("Copied artifact from {} to {}", sourcePath, targetPath);
                } else {
                    log.warn("Artifact path does not exist: {}", sourcePath);
                }
            }
        } catch (Exception e) {
            log.error("Error copying artifacts", e);
        }

        return copiedArtifacts;
    }
}
