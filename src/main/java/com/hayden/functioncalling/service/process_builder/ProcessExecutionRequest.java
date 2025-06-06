package com.hayden.functioncalling.service.process_builder;

import lombok.Builder;
import lombok.Data;

import java.io.File;
import java.util.List;
import java.util.Optional;

@Data
@Builder
public class ProcessExecutionRequest {
    private String command;
    private String arguments;
    private String workingDirectory;
    private Integer timeoutSeconds;
    private List<String> outputRegex;
    private List<String> successPatterns;
    private List<String> failurePatterns;
    private File outputFile;
    private Integer maxWaitForPatternSeconds;

    public Integer numWaitSeconds() {
        return Optional.ofNullable(getMaxWaitForPatternSeconds())
                .or(() -> Optional.ofNullable(getTimeoutSeconds()))
                .orElse(300);
    }

}
