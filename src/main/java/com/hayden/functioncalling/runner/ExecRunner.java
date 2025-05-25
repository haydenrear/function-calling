package com.hayden.functioncalling.runner;

import com.hayden.commitdiffmodel.codegen.types.CodeExecutionOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeExecutionResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public interface ExecRunner {

    CodeExecutionResult run(CodeExecutionOptions codeExecutionResult);

    Future<CodeExecutionResult> runAsync(CodeExecutionOptions codeExecutionResult);

}
