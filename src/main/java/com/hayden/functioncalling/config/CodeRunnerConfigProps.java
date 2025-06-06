package com.hayden.functioncalling.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "code-runner")
@Data
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeRunnerConfigProps {

    private List<TestExecutionRegistration> registrations = new ArrayList<>();
    private List<BuildRegistration> buildRegistrations = new ArrayList<>();
    private List<DeployRegistration> deployRegistrations = new ArrayList<>();

    @Data
    public static class TestExecutionRegistration {

        private String id;
        private String command;
        private String workingDirectory;
        private String description;
        private String arguments;
        private Integer timeoutSeconds = -1;
        private boolean enabled = true;
        private List<Path> reportingPaths = new ArrayList<>();
        private List<String> outputRegex = new ArrayList<>();
        private Path runnerCopyPath;
    }

    @Data
    public static class BuildRegistration {

        private String id;
        private String buildCommand;
        private String workingDirectory;
        private String description;
        private String arguments;
        private Integer timeoutSeconds = -1;
        private boolean enabled = true;
        private List<Path> artifactPaths = new ArrayList<>();
        private List<String> outputRegex = new ArrayList<>();
        private Path artifactOutputDirectory;
        private List<String> buildSuccessPatterns = new ArrayList<>();
        private List<String> buildFailurePatterns = new ArrayList<>();
    }

    @Data
    public static class DeployRegistration {

        private String id;
        private String deployCommand;
        private String workingDirectory;
        private String description;
        private String arguments;
        private Integer timeoutSeconds = -1;
        private boolean enabled = true;
        private List<String> deploySuccessPatterns = new ArrayList<>();
        private List<String> deployFailurePatterns = new ArrayList<>();
        private List<String> outputRegex = new ArrayList<>();
        private String healthCheckUrl;
        private Integer healthCheckTimeoutSeconds;
        private Integer maxWaitForStartupSeconds;
        private String stopCommand;
    }
}
