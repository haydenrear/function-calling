package com.hayden.functioncalling.service;

import com.hayden.utilitymodule.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
class TestReportServiceTest {

    @Autowired
    private TestReportService testReportService;

//    @Test
//    public void testRetrieveErrsOther() {
//        var failures = testReportService.getFailureContext("/Users/hayde/IdeaProjects/drools/commit-diff-context-graphql/build/reports/tests/test/index.html");
//        assertThat(failures).doesNotContain("No test failures found.");
//    }

    @Test
    public void testRetrieveErrs() {
        var failures = testReportService.getFailureContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/failure/index.html",
                FileUtils.getTestWorkDir().get().toFile().getAbsolutePath(),
                "test_session");
        assertThat(failures).doesNotContain("No test failures found.");
        var successes = testReportService.getFailureContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/success/index.html",
                FileUtils.getTestWorkDir().get().toFile().getAbsolutePath(),
                "test_session");
        assertThat(successes).contains("No test failures found.");
    }

}