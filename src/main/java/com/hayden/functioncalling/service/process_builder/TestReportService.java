package com.hayden.functioncalling.service.process_builder;

import com.hayden.functioncalling.utils.TestResultsProcessor;
import com.hayden.persistence.lock.AdvisoryLock;
import com.hayden.utilitymodule.io.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
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

            advisoryLock.doWithAdvisoryLock(() -> {
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

                return null;
            }, sessionId);

        return processed;
    }
}