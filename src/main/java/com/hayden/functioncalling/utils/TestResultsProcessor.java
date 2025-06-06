package com.hayden.functioncalling.utils;

import com.hayden.persistence.lock.WithPgAdvisory;
import com.hayden.utilitymodule.io.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Component that processes test results and extracts context information for failures.
 */
@Component
@Slf4j
public class TestResultsProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(TestResultsProcessor.class);
    
    /**
     * Process the test results and extract failure information
     * @param testReportDirectory The directory containing Gradle test reports
     * @return A formatted string with failure information
     */
    public String processTestFailures(String testReportDirectory) {
        logger.info("Processing test failures from: {}", testReportDirectory);
        
        TestFailureExtractor extractor = new TestFailureExtractor(testReportDirectory);
        Map<String, List<TestFailureExtractor.FailureDetail>> failures = extractor.extractFailures();
        
        if (failures.isEmpty()) {
            return "No test failures found.";
        }
        
        // Build a formatted string with all failure information
        StringBuilder result = new StringBuilder("Test Failure Summary:\n\n");
        
        failures.forEach((className, failureDetails) -> {
            result.append("Class: ").append(className).append("\n");
            
            failureDetails.forEach(detail -> {
                result.append("  - Test: ").append(detail.getTestName()).append("\n");
                result.append("    Error: ").append(detail.getErrorMessage()).append("\n\n");
            });
        });
        
        return result.toString();
    }

    @WithPgAdvisory
    public void copyTestResults(String sessionId, String processed, Path path, String runnerCopyPath) {
        try {
            FileUtils.writeToFile(processed, path.resolve(sessionId).resolve("processed-%s.txt".formatted(Timestamp.from(Instant.now()).toString())));
            var toMoveTo = Paths.get(runnerCopyPath, sessionId);
            Path copyPath;
            if (path.toFile().isFile())
                copyPath = path.getParent();
            else
                copyPath = path;
            if (copyPath.toFile().exists() || copyPath.toFile().isDirectory()) {
                log.error("Could not find valid test reporting path from {}: {}", path, copyPath);
            }
            FileUtils.copyAll(copyPath, toMoveTo);
        } catch (IOException e) {
            log.error("Failed to copy {} to {}, {}", path, runnerCopyPath, e.getMessage());
        }
    }

}