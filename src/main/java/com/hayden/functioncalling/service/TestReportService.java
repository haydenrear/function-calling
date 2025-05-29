package com.hayden.functioncalling.service;

import com.hayden.functioncalling.config.CodeRunnerConfigProps;
import com.hayden.functioncalling.utils.TestResultsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Service for handling test reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestReportService {
    
    private final TestResultsProcessor testResultsProcessor;

    private final CodeRunnerConfigProps configProps;
    

    /**
     * Get context information for failed tests
     * @param reportPath Path to the test report directory
     * @return Formatted failure information
     */
    public String getFailureContext(String reportPath, String sessionId, String runnerCopyPath) {
        Path path = Paths.get(reportPath);
        if (!path.toFile().exists()) {
            return null;
        }

        String absolutePath = path.toFile().getAbsolutePath();
        var t = testResultsProcessor.processTestFailures(absolutePath);

        try {
            Files.copy(path, Paths.get(runnerCopyPath, path.toFile().getName()), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to copy {} to {}, {}", path, runnerCopyPath, e.getMessage());
        }

        return t;
    }
}