package com.hayden.functioncalling;

import com.hayden.utilitymodule.ctx.PresetProperties;
import org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
        VertexAiGeminiAutoConfiguration.class, OtlpMetricsExportAutoConfiguration.class})
@Import(PresetProperties.class)
public class FunctionCallingApplication {

    public static void main(String[] args) {
        SpringApplication.run(FunctionCallingApplication.class, args);
    }

}
