package com.hayden.functioncalling.runner.process_builder;

import com.hayden.commitdiffmodel.codegen.types.CodeBuildOptions;
import com.hayden.commitdiffmodel.codegen.types.CodeBuildResult;
import com.hayden.commitdiffmodel.codegen.types.Error;
import com.hayden.functioncalling.entity.CodeBuildEntity;
import com.hayden.functioncalling.repository.CodeBuildRepository;
import com.hayden.functioncalling.runner.BuildExecRunner;
import com.hayden.functioncalling.service.process_builder.exec.ProcessBuilderBuildExecutionService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessBuilderBuildExecRunner implements BuildExecRunner {

    private final CodeBuildRepository codeBuildRepository;
    private final ProcessBuilderBuildExecutionService buildExecutionService;
    private final ThreadPoolTaskExecutor asyncRunnerTaskExecutor;

    @Override
    public CompletableFuture<CodeBuildResult> buildAsync(
        CodeBuildOptions codeBuildOptions
    ) {
        return asyncRunnerTaskExecutor.submitCompletable(() ->
            this.build(codeBuildOptions)
        );
    }

    @Override
    public CodeBuildResult build(CodeBuildOptions options) {
        if (options.getRegistrationId() == null) {
            return CodeBuildResult.newBuilder()
                .success(false)
                .error(List.of(new Error("Registration ID is required")))
                .build();
        }

        Optional<CodeBuildEntity> buildEntityOpt =
            codeBuildRepository.findByRegistrationId(options.getRegistrationId());

        if (buildEntityOpt.isEmpty()) {
            return CodeBuildResult.newBuilder()
                .success(false)
                .error(
                    List.of(
                        new Error(
                            "No code build registration found with ID: " +
                            options.getRegistrationId()
                        )
                    )
                )
                .build();
        }

        CodeBuildEntity buildEntity = buildEntityOpt.get();

        if (!buildEntity.getEnabled()) {
            return CodeBuildResult.newBuilder()
                .success(false)
                .registrationId(options.getRegistrationId())
                .error(
                    List.of(
                        new Error(
                            "Code build registration is disabled: " +
                            options.getRegistrationId()
                        )
                    )
                )
                .build();
        }

        return buildExecutionService.execute(buildEntity, options);
    }
}
