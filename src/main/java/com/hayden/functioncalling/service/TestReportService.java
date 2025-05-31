package com.hayden.functioncalling.service;

import com.hayden.functioncalling.config.CodeRunnerConfigProps;
import com.hayden.functioncalling.utils.TestResultsProcessor;
import com.hayden.persistence.lock.AdvisoryLock;
import com.hayden.utilitymodule.io.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Service for handling test reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestReportService {
    
    private final TestResultsProcessor testResultsProcessor;

    private final AdvisoryLock advisoryLock;
    

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

        try {
            advisoryLock.doLock(sessionId);
            FileUtils.writeToFile(processed, path.resolve("processed-%s.txt".formatted(Timestamp.from(Instant.now()).toString())));
            var toMoveTo = Paths.get(runnerCopyPath, sessionId);
            FileUtils.copyAll(path, toMoveTo);
            advisoryLock.doUnlock(sessionId);
        } catch (IOException e) {
            log.error("Failed to copy {} to {}, {}", path, runnerCopyPath, e.getMessage());
        }

        return processed;
    }
}