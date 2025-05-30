package com.hayden.functioncalling.config;


import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "code-runner")
@Data
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeRunnerConfigProps {


    private List<Registration> registrations = new ArrayList<>();
    
    @Data
    public static class Registration {
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

}
