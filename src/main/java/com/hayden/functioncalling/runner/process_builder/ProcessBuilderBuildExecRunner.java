package com.hayden.functioncalling.runner.process_builder;

import com.hayden.commitdiffmodel.codegen.types.CodeBuildOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeBuildResult;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.functioncalling.entity.CodeBuildEntity;
import com.hayden.functioncalling.repository.CodeBuildRepository;
import com.hayden.functioncalling.runner.BuildExecRunner;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderDataService;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderBuildExecRunner implements BuildExecRunner {

    private final CodeBuildRepository codeBuildRepository;
    private final ProcessBuilderDataService buildDataService;
    private final ThreadPoolTaskExecutor runnerTaskExecutor;
    private final ThreadPoolTaskExecutor asyncRunnerTaskExecutor;

    @Override
    public CompletableFuture<CodeBuildResult> buildAsync(CodeBuildOptions codeBuildOptions) {
        return asyncRunnerTaskExecutor.submitCompletable(() -> this.build(codeBuildOptions));
    }

    @Override
    public CodeBuildResult build(CodeBuildOptions options) {
        if (options.getRegistrationId() == null) {
            return CodeBuildResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("Registration ID is required")))
                    .build();
        }

        Optional<CodeBuildEntity> buildEntityOpt = codeBuildRepository.findByRegistrationId(options.getRegistrationId());

        if (buildEntityOpt.isEmpty()) {
            return CodeBuildResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("No code build registration found with ID: " + options.getRegistrationId())))
                    .build();
        }

        CodeBuildEntity buildEntity = buildEntityOpt.get();

        if (!buildEntity.getEnabled()) {
            return CodeBuildResult.newBuilder()
                    .success(false)
                    .registrationId(options.getRegistrationId())
                    .error(List.of(new Error("Code build registration is disabled: " + options.getRegistrationId())))
                    .build();
        }

        try {
            return executeBuild(buildEntity, options);
        } catch (Exception e) {
            log.error("Error executing build command", e);
            return CodeBuildResult.newBuilder()
                    .success(false)
                    .registrationId(options.getRegistrationId())
                    .error(List.of(new Error("Error executing build command: " + e.getMessage())))
                    .build();
        }
    }

    private CodeBuildResult executeBuild(CodeBuildEntity entity, CodeBuildOptions options) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        String buildId = UUID.randomUUID().toString();

        List<String> commandParts = new ArrayList<>(Arrays.asList(entity.getBuildCommand().split("\\s+")));

        // Add additional arguments if provided
        String arguments = null;
        if (StringUtils.isNotBlank(options.getArguments())) {
            arguments = options.getArguments();
            commandParts.addAll(Arrays.asList(options.getArguments().split("\\s+")));
        } else if (StringUtils.isNotBlank(entity.getArguments())) {
            arguments = entity.getArguments();
            commandParts.addAll(Arrays.asList(entity.getArguments().split("\\s+")));
        }

        log.info("Executing build command: {}", String.join(" ", commandParts));

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);

        // Set working directory if specified
        if (StringUtils.isNotBlank(entity.getWorkingDirectory())) {
            processBuilder.directory(new File(entity.getWorkingDirectory()));
        }

        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        int timeoutSeconds = options.getTimeoutSeconds() != null ? options.getTimeoutSeconds() :
                             entity.getTimeoutSeconds() != null ? entity.getTimeoutSeconds() : -1;

        // Read process output in real-time
        StringBuilder output = new StringBuilder();
        StringBuilder buildLog = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // Start a thread to read the process output
        Thread outputThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    buildLog.append(line).append("\n");

                    if (entity.getOutputRegex() != null && !entity.getOutputRegex().isEmpty()) {
                        String finalLine = line;
                        if (entity.getOutputRegex().stream().anyMatch(finalLine::matches)) {
                            output.append(finalLine).append("\n");
                        }
                    } else {
                        output.append(line).append("\n");
                    }
                }
            } catch (IOException e) {
                log.error("Error reading build process output", e);
            }
        });

        var outputFuture = this.runnerTaskExecutor.submitCompletable(outputThread);

        // Wait for the process to complete
        boolean completed;
        if (timeoutSeconds != -1) {
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            completed = process.waitFor() == 0;
        }

        // Wait for the output thread to finish reading
        try {
            outputFuture.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("Interrupted while waiting for build output thread to complete", e);
        }

        int exitCode;
        String error = null;
        boolean success;
        int executionTimeMs = (int)(System.currentTimeMillis() - startTime);

        if (!completed) {
            process.destroyForcibly();
            error = "Build execution timed out after " + timeoutSeconds + " seconds";
            success = false;
            exitCode = -1;
        } else {
            exitCode = process.exitValue();
            success = (exitCode == 0);

            // Check for build success/failure patterns
            String buildOutput = buildLog.toString();
            if (entity.getBuildSuccessPatterns() != null && !entity.getBuildSuccessPatterns().isEmpty()) {
                boolean foundSuccessPattern = entity.getBuildSuccessPatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(buildOutput).find());
                if (!foundSuccessPattern && success) {
                    success = false;
                    error = "Build completed but success pattern not found in output";
                }
            }

            if (entity.getBuildFailurePatterns() != null && !entity.getBuildFailurePatterns().isEmpty()) {
                boolean foundFailurePattern = entity.getBuildFailurePatterns().stream()
                        .anyMatch(pattern -> Pattern.compile(pattern).matcher(buildOutput).find());
                if (foundFailurePattern) {
                    success = false;
                    error = "Build failure pattern detected in output";
                }
            }

            if (!success && error == null) {
                error = "Build command exited with non-zero status: " + exitCode;
            }
        }

        // Handle artifacts if build was successful
        List<String> copiedArtifacts = new ArrayList<>();
        if (success && entity.getArtifactPaths() != null && !entity.getArtifactPaths().isEmpty()) {
            copiedArtifacts = copyArtifacts(entity, buildId);
        }

        // Save build history
        buildDataService.saveBuildHistory(
                entity.getRegistrationId(), buildId, entity.getBuildCommand(), arguments,
                output.toString(), error, success, exitCode, executionTimeMs, options.getSessionId(),
                copiedArtifacts, entity.getArtifactOutputDirectory(), buildLog.toString());

        return CodeBuildResult.newBuilder()
                .registrationId(options.getRegistrationId())
                .success(success)
                .output(output.toString())
                .sessionId(options.getSessionId())
                .exitCode(exitCode)
                .executionTime(executionTimeMs)
                .buildId(buildId)
                .error(List.of(new Error(error)))
                .artifactPaths(copiedArtifacts)
                .artifactOutputDirectory(entity.getArtifactOutputDirectory())
                .buildLog(buildLog.toString())
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
        } catch (IOException e) {
            log.error("Error copying artifacts", e);
        }

        return copiedArtifacts;
    }
}
