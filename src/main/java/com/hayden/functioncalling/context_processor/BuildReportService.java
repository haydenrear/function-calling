package com.hayden.functioncalling.context_processor;

import com.hayden.functioncalling.utils.TestResultsProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for handling test reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildReportService {
    

    /**
     * Get context information for failed tests
     * @param reportPath Path to the test report directory
     * @return Formatted failure information
     */
    public String getContext(String reportPath, String sessionId, String runnerCopyPath) {
        return "";
    }
}