package com.hayden.functioncalling.service.process_builder;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessExecutionResult {
    private boolean success;
    private String output;
    private String fullLog;
    private String error;
    private int exitCode;
    private int executionTimeMs;
    private Process process;
}
