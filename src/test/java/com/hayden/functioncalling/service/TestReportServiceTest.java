package com.hayden.functioncalling.service;

import com.hayden.functioncalling.context_processor.TestReportService;
import com.hayden.utilitymodule.io.FileUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
class TestReportServiceTest {

    @Autowired
    private TestReportService testReportService;

    @SneakyThrows
    @Test
    public void testRetrieveErrs() {
        Optional<Path> isTestWorkDir = FileUtils.getTestWorkDir();
        if (isTestWorkDir.isEmpty()) {
            new File("test_work").mkdirs();
        }
        isTestWorkDir = FileUtils.getTestWorkDir();
        assertThat(isTestWorkDir.isPresent()).isTrue();
        Path testWorkDir = isTestWorkDir.get();
        var failures = testReportService.getContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/failure/index.html",
                "test_session",
                testWorkDir.toFile().getAbsolutePath());
        assertThat(failures).doesNotContain("No test failures found.");
        var successes = testReportService.getContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/success/index.html",
                "test_session",
                testWorkDir.toFile().getAbsolutePath());
        assertThat(successes).contains("No test failures found.");
        assertThat(testWorkDir.resolve("test_session").resolve("index.html").toFile()).exists();
        assertThat(testWorkDir.resolve("test_session").resolve("classes").toFile()).exists();
    }

}