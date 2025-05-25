package com.hayden.functioncalling.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
class TestReportServiceTest {

    @Autowired
    private TestReportService testReportService;

    @Test
    public void testRetrieveErrs() {
        var failures = testReportService.getFailureContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/failure/index.html");
        assertThat(failures).doesNotContain("No test failures found.");
        var successes = testReportService.getFailureContext("/Users/hayde/IdeaProjects/drools/function-calling/src/test/resources/test-reports/success/index.html");
        assertThat(successes).contains("No test failures found.");
    }

}