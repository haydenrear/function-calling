package com.hayden.functioncalling.service;

import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionServiceFactory {

    private final List<ExecutionService<?, ?, ?>> executionServices;

    @SuppressWarnings("unchecked")
    public <T, R, O> Optional<ExecutionService<T, R, O>> getExecutionService(ExecutionType executionType) {
        return executionServices.stream()
                .filter(service -> service.canHandle(executionType))
                .map(service -> (ExecutionService<T, R, O>) service)
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public <T, R, O> ExecutionService<T, R, O> getExecutionServiceOrThrow(ExecutionType executionType) {
        return (ExecutionService<T, R, O>) executionServices.stream()
                .filter(service -> service.canHandle(executionType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No execution service found for type: " + executionType));
    }

    public boolean isSupported(ExecutionType executionType) {
        return executionServices.stream()
                .anyMatch(service -> service.canHandle(executionType));
    }

    public List<ExecutionType> getSupportedTypes() {
        return executionServices.stream()
                .map(ExecutionService::getExecutionType)
                .distinct()
                .toList();
    }
}
