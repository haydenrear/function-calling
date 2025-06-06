package com.hayden.functioncalling.service.process_builder;

import lombok.Builder;
import lombok.Data;

import java.io.File;
import java.util.List;

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
}
