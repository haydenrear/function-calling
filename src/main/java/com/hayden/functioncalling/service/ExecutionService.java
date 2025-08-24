package com.hayden.functioncalling.service;

import com.hayden.commitdiffmodel.codegen.types.CodeBuildOptions;
import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import com.hayden.functioncalling.entity.CodeBuildEntity;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public interface ExecutionService<T, R, O> {

    /**
     * Execute the given entity with options
     * @param entity The entity containing execution configuration
     * @param options The options for this specific execution
     * @return The execution result
     */
    R execute(T entity, O options);

    /**
     * Get the execution type this service handles
     * @return The execution type
     */
    ExecutionType getExecutionType();

    /**
     * Check if this service can handle the given execution type
     * @param executionType The execution type to check
     * @return true if this service can handle the type
     */
    default boolean canHandle(ExecutionType executionType) {
        return getExecutionType().equals(executionType);
    }

    static @Nullable File getLogFile(CodeBuildEntity entity, CodeBuildOptions options) throws IOException {
        File file = null;
        if (options.getWriteToFile()) {
            file = Paths.get(entity.getArtifactOutputDirectory()).getParent().resolve("%s-log.log".formatted(entity.getRegistrationId())).toFile();
            if (file.exists())
                Files.delete(file.toPath());
        }
        return file;
    }
}
