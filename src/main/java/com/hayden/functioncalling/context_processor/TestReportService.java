package com.hayden.functioncalling.service.process_builder;

import com.hayden.functioncalling.utils.TestResultsProcessor;
import com.hayden.persistence.lock.AdvisoryLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.*;

/**
 * Service for handling test reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestReportService {
    
    private final TestResultsProcessor testResultsProcessor;


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
        var processed = testResultsProcessor.processTestFailures(absolutePath);
        testResultsProcessor.copyTestResults(sessionId, processed, path, runnerCopyPath);
        return processed;
    }
}