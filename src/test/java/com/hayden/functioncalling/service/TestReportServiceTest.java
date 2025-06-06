package com.hayden.functioncalling.service;

import com.hayden.functioncalling.service.process_builder.TestReportService;
import com.hayden.utilitymodule.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
class TestReportServiceTest {

    @Autowired
    private TestReportService testReportService;

    @Test
    public void testRetrieveErrs() {
        Optional<Path> isTestWorkDir = FileUtils.getTestWorkDir();
        assertThat(isTestWorkDir.isPresent()).isTrue();
        Path testWorkDir = isTestWorkDir.get();
        var failures = testReportService.getFailureContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/failure/index.html",
                "test_session",
                testWorkDir.toFile().getAbsolutePath());
        assertThat(failures).doesNotContain("No test failures found.");
        var successes = testReportService.getFailureContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/success/index.html",
                "test_session",
                testWorkDir.toFile().getAbsolutePath());
        assertThat(successes).contains("No test failures found.");
        assertThat(testWorkDir.resolve("test_session").resolve("index.html").toFile()).exists();
        assertThat(testWorkDir.resolve("test_session").resolve("classes").toFile()).exists();
    }

}