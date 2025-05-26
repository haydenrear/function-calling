package com.hayden.functioncalling.config;

import com.hayden.commitdiffmodel.message.ModelServerEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.function.Predicate;

@Configuration
public class PgVectorConfig {

    @Bean
    @ConditionalOnBean(ModelServerEmbeddingModel.class)
    public VectorStore vectorStore(
            JdbcTemplate template,
            ModelServerEmbeddingModel embeddingModel,
            ModelServerConfigProperties modelServerConfigProperties
    ) {
        return modelServerConfigProperties.models.stream()
                .filter(ModelDescriptor::isDefaultModel)
                .findFirst()
                .map(md -> PgVectorStore.builder(template, embeddingModel).dimensions(md.getDimensions()).build())
                .or(() -> !modelServerConfigProperties.models.isEmpty()
                          ? Optional.of(PgVectorStore.builder(template, embeddingModel).dimensions( modelServerConfigProperties.models.getFirst().getDimensions()).build())
                          : Optional.empty())
                .orElseThrow(() -> new RuntimeException(modelServerConfigProperties.models.isEmpty()
                                                        ? "No model server properties found"
                                                        : "Please set one of model servers as default with default-model proeprty."));
    }

    @Bean
    @ConditionalOnBean(ModelServerEmbeddingModel.class)
    public BeanFactoryPostProcessor registerPostgresStores(
            @Lazy JdbcTemplate template,
            ModelServerEmbeddingModel embeddingModel,
            ModelServerConfigProperties modelServerConfigProperties
    ) {
        return beanFactory -> modelServerConfigProperties.models.stream()
                .filter(Predicate.not(ModelDescriptor::isDefaultModel))
                .forEach(md -> beanFactory.registerSingleton("vectorStore%s".formatted(md.getName()),
                        PgVectorStore.builder(template, embeddingModel).dimensions(md.getDimensions()).build()));
    }

}
