package com.hayden.functioncalling.service;

import com.hayden.functioncalling.utils.TestResultsProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for handling test reports
 */
@Service
@RequiredArgsConstructor
public class TestReportService {
    
    private final TestResultsProcessor testResultsProcessor;
    

    /**
     * Get context information for failed tests
     * @param reportPath Path to the test report directory
     * @return Formatted failure information
     */
    public String getFailureContext(String reportPath) {
        Path path = Paths.get(reportPath);
        if (!path.toFile().exists()) {
            return null;
        }

        if (path.toFile().isFile()) {
            path = path.getParent();
        }

        return testResultsProcessor.processTestFailures(path.toFile().getAbsolutePath());
    }
}