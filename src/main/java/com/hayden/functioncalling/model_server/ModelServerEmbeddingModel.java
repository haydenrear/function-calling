package com.hayden.functioncalling.model_server;

import lombok.experimental.Delegate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class ModelServerEmbeddingModel implements EmbeddingModel, VectorStore {

    @Delegate
    PgVectorStore pgVectorStore;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return null;
    }

    @Override
    public float[] embed(Document document) {
        return new float[0];
    }
}
