package com.hayden.functioncalling;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {OtlpMetricsExportAutoConfiguration.class,
                                  PgVectorStoreAutoConfiguration.class})
public class FunctionCallingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FunctionCallingApplication.class, args);
    }
}