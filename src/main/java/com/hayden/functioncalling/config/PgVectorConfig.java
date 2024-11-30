package com.hayden.functioncalling.config;

import com.hayden.functioncalling.model_server.ModelServerEmbeddingModel;
import lombok.SneakyThrows;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.function.Predicate;

@Configuration
public class PgVectorConfig {

    @Bean
    public PgVectorStore vectorStore(
            JdbcTemplate template,
            ModelServerEmbeddingModel embeddingModel,
            ModelServerConfigProperties modelServerConfigProperties
    ) {
        return modelServerConfigProperties.models.stream()
                .filter(ModelDescriptor::isDefaultModel)
                .findFirst()
                .map(md -> new PgVectorStore(template, embeddingModel, md.getDimensions()))
                .or(() -> !modelServerConfigProperties.models.isEmpty()
                          ? Optional.of(new PgVectorStore(template, embeddingModel, modelServerConfigProperties.models.getFirst().getDimensions()))
                          : Optional.empty())
                .orElseThrow(() -> new RuntimeException(modelServerConfigProperties.models.isEmpty()
                                                        ? "No model server properties found"
                                                        : "Please set one of model servers as default with default-model proeprty."));
    }

    @Bean
    public BeanFactoryPostProcessor registerPostgresStores(
            @Lazy JdbcTemplate template,
            ModelServerEmbeddingModel embeddingModel,
            ModelServerConfigProperties modelServerConfigProperties
    ) {
        return beanFactory -> modelServerConfigProperties.models.stream()
                .filter(Predicate.not(ModelDescriptor::isDefaultModel))
                .forEach(md -> beanFactory.registerSingleton("vectorStore%s".formatted(md.getName()), new PgVectorStore(template, embeddingModel, md.getDimensions())));
    }

}
