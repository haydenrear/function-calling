package com.hayden.functioncalling.runner;

import com.hayden.commitdiffmodel.codegen.types.CodeExecutionOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionResult;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface ExecRunner {

    CodeExecutionResult run(CodeExecutionOptions codeExecutionResult);

    CompletableFuture<CodeExecutionResult> runAsync(CodeExecutionOptions codeExecutionResult);

}
