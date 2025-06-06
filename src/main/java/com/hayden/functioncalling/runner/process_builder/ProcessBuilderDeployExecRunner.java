package com.hayden.functioncalling.runner.process_builder;

import com.hayden.commitdiffmodel.codegen.types.CodeDeployOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeDeployResult;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.functioncalling.entity.CodeDeployEntity;
import com.hayden.functioncalling.repository.CodeDeployRepository;
import com.hayden.functioncalling.runner.DeployExecRunner;
import com.hayden.functioncalling.service.process_builder.ProcessBuilderDataService;
import com.hayden.functioncalling.service.process_builder.exec.ProcessBuilderDeployExecutionService;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderDeployExecRunner implements DeployExecRunner {

    private final CodeDeployRepository codeDeployRepository;
    private final ThreadPoolTaskExecutor asyncRunnerTaskExecutor;
    private final ProcessBuilderDeployExecutionService executionService;

    @Override
    public CompletableFuture<CodeDeployResult> deployAsync(CodeDeployOptions codeDeployOptions) {
        return asyncRunnerTaskExecutor.submitCompletable(() -> this.deploy(codeDeployOptions));
    }

    @Override
    public CodeDeployResult deploy(CodeDeployOptions options) {
        if (options.getRegistrationId() == null) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("Registration ID is required")))
                    .build();
        }

        Optional<CodeDeployEntity> deployEntityOpt = codeDeployRepository.findByRegistrationId(options.getRegistrationId());

        if (deployEntityOpt.isEmpty()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("No code deploy registration found with ID: " + options.getRegistrationId())))
                    .build();
        }

        CodeDeployEntity deployEntity = deployEntityOpt.get();

        if (!deployEntity.getEnabled()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(options.getRegistrationId())
                    .error(List.of(new Error("Code deploy registration is disabled: " + options.getRegistrationId())))
                    .build();
        }

        try {
            return executionService.execute(deployEntity, options);
        } catch (Exception e) {
            log.error("Error executing deploy command", e);
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .registrationId(options.getRegistrationId())
                    .error(List.of(new Error("Error executing deploy command: " + e.getMessage())))
                    .build();
        }
    }

    @Override
    public CodeDeployResult stopDeployment(String registrationId, String sessionId) {
        Optional<CodeDeployEntity> deployEntityOpt = codeDeployRepository.findByRegistrationId(registrationId);

        if (deployEntityOpt.isEmpty()) {
            return CodeDeployResult.newBuilder()
                    .success(false)
                    .error(List.of(new Error("No code deploy registration found with ID: " + registrationId)))
                    .build();
        }

        CodeDeployEntity deployEntity = deployEntityOpt.get();

        return executionService.stopDeployment(deployEntity, sessionId);
    }


}
