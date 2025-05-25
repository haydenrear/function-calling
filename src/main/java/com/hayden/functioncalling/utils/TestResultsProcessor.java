package com.hayden.functioncalling.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Component that processes test results and extracts context information for failures.
 */
@Component
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
}