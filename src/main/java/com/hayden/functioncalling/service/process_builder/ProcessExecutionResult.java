package com.hayden.functioncalling.service.process_builder;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

@Data
@Builder(toBuilder = true)
public class ProcessExecutionResult {
    private boolean success;
    private String matchedOutput;
    private String fullLog;
    private Path logPath;
    private String error;
    private int exitCode;
    private int executionTimeMs;
    private Process process;
    boolean didWriteToFile;
}
