package com.hayden.functioncalling.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class PgVectorConfig {

//    @Bean
//    public VectorStore vectorStore(
//            JdbcTemplate template,
//            ModelServerEmbeddingModel embeddingModel,
//            ModelServerConfigProperties modelServerConfigProperties
//    ) {
//        return modelServerConfigProperties.models.stream()
//                .filter(ModelDescriptor::isDefaultModel)
//                .findFirst()
//                .map(md -> new PgVectorStore(template, embeddingModel, md.getDimensions()))
//                .or(() -> !modelServerConfigProperties.models.isEmpty()
//                          ? Optional.of(new PgVectorStore(template, embeddingModel, modelServerConfigProperties.models.getFirst().getDimensions()))
//                          : Optional.empty())
//                .orElseThrow(() -> new RuntimeException(modelServerConfigProperties.models.isEmpty()
//                                                        ? "No model server properties found"
//                                                        : "Please set one of model servers as default with default-model proeprty."));
//    }
//
//    @Bean
//    public BeanFactoryPostProcessor registerPostgresStores(
//            @Lazy JdbcTemplate template,
//            ModelServerEmbeddingModel embeddingModel,
//            ModelServerConfigProperties modelServerConfigProperties
//    ) {
//        return beanFactory -> modelServerConfigProperties.models.stream()
//                .filter(Predicate.not(ModelDescriptor::isDefaultModel))
//                .forEach(md -> beanFactory.registerSingleton("vectorStore%s".formatted(md.getName()), new PgVectorStore(template, embeddingModel, md.getDimensions())));
//    }

}
