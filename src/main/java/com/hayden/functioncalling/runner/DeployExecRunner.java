package com.hayden.functioncalling.runner;

import com.hayden.commitdiffmodel.codegen.types.CodeDeployOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeDeployResult;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CompletableFuture;

public interface DeployExecRunner {

    CodeDeployResult deploy(CodeDeployOptions codeDeployOptions);

    CompletableFuture<CodeDeployResult> deployAsync(CodeDeployOptions codeDeployOptions);

    CodeDeployResult stopDeployment(String registrationId, String sessionId);

}
