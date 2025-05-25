package com.hayden.functioncalling.utils;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility class to extract failure information from Gradle test reports.
 */
@Slf4j
public class TestFailureExtractor {
    
    private String testReportDir;
    
    public TestFailureExtractor(String testReportDir) {
        this.testReportDir = testReportDir;
    }
    
    /**
     * Extract failure information from test reports.
     * @return Map of test class to failure details
     */
    public Map<String, List<FailureDetail>> extractFailures() {
        Map<String, List<FailureDetail>> failures = new HashMap<>();

        if (testReportDir.endsWith("log") || testReportDir.endsWith("txt")) {
            try {
                return Map.of(testReportDir, Lists.newArrayList(new FailureDetail(testReportDir, Files.readString(Paths.get(testReportDir))))) ;
            } catch (IOException e) {
                return cannotFindTestReport();
            }
        } else if (testReportDir.endsWith("index.html")) {
            try {
                // Parse index.html to find packages with failures
                File indexFile = new File(testReportDir);
                testReportDir = Paths.get(testReportDir).getParent().toFile().getAbsolutePath();
                Document indexDoc = Jsoup.parse(indexFile, "UTF-8");

                // Get all package rows
                Elements packageRows = indexDoc.select("td.failures");

                for (Element failureCountElement : packageRows) {
                    Elements tdA = failureCountElement.select("td a");
                    if (!tdA.isEmpty()) {
                        Element first = tdA.first();
                        String packageLink = first.attr("href");
                        String packageName = first.text();
                        processPackageFailures(packageLink, packageName, failures);
                    }
                }

            } catch (IOException e) {
                log.error("Error parsing test reports", e);
            }

            return failures;
        } else {
            return cannotFindTestReport();
        }
        
    }

    private @NotNull Map<String, List<FailureDetail>> cannotFindTestReport() {
        return Map.of(testReportDir, Lists.newArrayList(new FailureDetail(testReportDir, "Error loading test report from %s".formatted(testReportDir))));
    }

    private void processPackageFailures(String packageLink, String packageName, 
                                      Map<String, List<FailureDetail>> failures) throws IOException {
        File packageFile = new File(testReportDir, packageLink);
        Document packageDoc = Jsoup.parse(packageFile, "UTF-8");
        
        // Get all class rows
        var classRows = packageDoc.select("td.failures");

        for (Element classRow : classRows) {

            // This class has failures, get the link to class.html
            Elements tdA = classRow.select("td a");
            if (tdA.isEmpty()) {
                continue;
            }
            String classLink = tdA.first().attr("href");
            Elements tdASecond = classRow.select("td a");
            if (tdASecond.isEmpty()) {
                continue;
            }

            String className =  tdASecond.first().text();
            // Process the class HTML file
            processClassFailures(packageLink, classLink, className, failures);
        }
    }
    
    private void processClassFailures(String packageLink, String classLink, String className,
                                    Map<String, List<FailureDetail>> failures) throws IOException {
        File classFile = new File(testReportDir, packageLink).getParentFile().toPath().resolve(classLink).toFile();
        Document classDoc = Jsoup.parse(classFile, "UTF-8");
        
        // Get all test rows
        Elements testRows = classDoc.select("h3.failures");
        List<FailureDetail> classFailures = new ArrayList<>();
        
        for (Element testRow : testRows) {
            Optional.ofNullable(testRow.parent())
                    .map(e -> e.select(".code").select("pre").text())
                    .filter(s -> !s.contains("ApplicationContext failure threshold"))
                    .map(found -> new FailureDetail(found, className))
                    .ifPresent(classFailures::add);
        }
        
        if (!classFailures.isEmpty()) {
            failures.put(className, classFailures);
        }
    }
    
    /**
     * Data class to store failure details
     */
    public static class FailureDetail {
        private final String testName;
        private final String errorMessage;
        
        public FailureDetail(String testName, String errorMessage) {
            this.testName = testName;
            this.errorMessage = errorMessage;
        }
        
        public String getTestName() {
            return testName;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            return "FailureDetail{" +
                    "testName='" + testName + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}