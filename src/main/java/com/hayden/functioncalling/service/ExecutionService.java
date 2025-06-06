package com.hayden.functioncalling.service;

import com.hayden.commitdiffmodel.codegen.types.ExecutionType;

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
}
