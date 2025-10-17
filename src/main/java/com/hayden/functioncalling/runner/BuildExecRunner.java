package com.hayden.functioncalling.runner;

import com.hayden.commitdiffmodel.codegen.types.CodeBuildOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeBuildResult;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CompletableFuture;

public interface BuildExecRunner {

    CodeBuildResult build(CodeBuildOptions codeBuildOptions);

    CompletableFuture<CodeBuildResult> buildAsync(CodeBuildOptions codeBuildOptions);

}
