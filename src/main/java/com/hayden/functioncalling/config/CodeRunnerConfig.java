package com.hayden.functioncalling.config;

import com.hayden.commitdiffcontext.convert.CommitDiffContextMapper;
import com.hayden.functioncalling.entity.CodeBuildEntity;
import com.hayden.functioncalling.entity.CodeDeployEntity;
import com.hayden.functioncalling.entity.TestExecutionEntity;
import com.hayden.functioncalling.repository.CodeBuildRepository;
import com.hayden.functioncalling.repository.CodeDeployRepository;
import com.hayden.functioncalling.repository.TestExecutionRepository;
import com.hayden.persistence.lock.AdvisoryLock;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import com.hayden.persistence.lock.WithPgAdvisoryAspect;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Slf4j
@Import({AdvisoryLock.class, WithPgAdvisoryAspect.class})
public class CodeRunnerConfig {

    @Bean
    public CommitDiffContextMapper mapper() {
        return new CommitDiffContextMapper(new ModelMapper());
    }

    @Bean
    public CommandLineRunner add(
        TestExecutionRepository testExecutionRepository,
        CodeBuildRepository codeBuildRepository,
        CodeDeployRepository codeDeployRepository,
        CodeRunnerConfigProps registrations
    ) {
        // Initialize execution registrations
        log.info(
            "Initializing code runner registrations from configuration: {}",
            registrations.getTestRegistrations()
        );
        for (CodeRunnerConfigProps.TestExecutionRegistration reg : registrations.getTestRegistrations()) {
            if (testExecutionRepository.existsByRegistrationId(reg.getRegistrationId())) {
                log.info(
                    "Registration with ID {} already exists, skipping",
                    reg.getRegistrationId()
                );
                continue;
            }

            TestExecutionEntity entity = TestExecutionEntity.builder()
                .registrationId(reg.getRegistrationId())
                .sessionId("STARTUP")
                .command(reg.getCommand())
                .workingDirectory(reg.getWorkingDirectory())
                .description(reg.getDescription())
                .arguments(reg.getArguments())
                .timeoutSeconds(reg.getTimeoutSeconds())
                .enabled(reg.isEnabled())
                .runnerCopyPath(
                    Optional.ofNullable(reg.getRunnerCopyPath())
                        .map(Path::toFile)
                        .map(File::getAbsolutePath)
                        .orElse(null)
                )
                .reportingPaths(
                    reg
                        .getReportingPaths()
                        .stream()
                        .map(p -> p.toFile().getAbsolutePath())
                        .toList()
                )
                .outputRegex(reg.getOutputRegex())
                .build();

            testExecutionRepository.save(entity);
            log.info("Added code execution registration: {}", reg.getRegistrationId());
        }

        // Initialize build registrations
        log.info(
            "Initializing code build registrations from configuration: {}",
            registrations.getBuildRegistrations()
        );
        for (CodeRunnerConfigProps.BuildRegistration reg : registrations.getBuildRegistrations()) {
            if (codeBuildRepository.existsByRegistrationId(reg.getRegistrationId())) {
                log.info(
                    "Build registration with ID {} already exists, skipping",
                    reg.getRegistrationId()
                );
                continue;
            }

            CodeBuildEntity entity = CodeBuildEntity.builder()
                .registrationId(reg.getRegistrationId())
                .sessionId("STARTUP")
                .buildCommand(reg.getBuildCommand())
                .workingDirectory(reg.getWorkingDirectory())
                .description(reg.getDescription())
                .arguments(reg.getArguments())
                .timeoutSeconds(reg.getTimeoutSeconds())
                .enabled(reg.isEnabled())
                .artifactPaths(
                    reg
                        .getArtifactPaths()
                        .stream()
                        .map(p -> p.toFile().getAbsolutePath())
                        .toList()
                )
                .artifactOutputDirectory(
                    Optional.ofNullable(reg.getArtifactOutputDirectory())
                        .map(Path::toFile)
                        .map(File::getAbsolutePath)
                        .orElse(null)
                )
                .outputRegex(reg.getOutputRegex())
                .buildSuccessPatterns(reg.getBuildSuccessPatterns())
                .buildFailurePatterns(reg.getBuildFailurePatterns())
                .build();

            codeBuildRepository.save(entity);
            log.info("Added code build registration: {}", reg.getRegistrationId());
        }

        // Initialize deploy registrations
        log.info(
            "Initializing code deploy registrations from configuration: {}",
            registrations.getDeployRegistrations()
        );
        for (CodeRunnerConfigProps.DeployRegistration reg : registrations.getDeployRegistrations()) {
            if (codeDeployRepository.existsByRegistrationId(reg.getRegistrationId())) {
                log.info(
                    "Deploy registration with ID {} already exists, skipping",
                    reg.getRegistrationId()
                );
                continue;
            }

            CodeDeployEntity entity = CodeDeployEntity.builder()
                .registrationId(reg.getRegistrationId())
                .sessionId("STARTUP")
                .deployCommand(reg.getDeployCommand())
                .workingDirectory(reg.getWorkingDirectory())
                .description(reg.getDescription())
                .arguments(reg.getArguments())
                .timeoutSeconds(reg.getTimeoutSeconds())
                .enabled(reg.isEnabled())
                .deploySuccessPatterns(reg.getDeploySuccessPatterns())
                .deployFailurePatterns(reg.getDeployFailurePatterns())
                .outputRegex(reg.getOutputRegex())
                .healthCheckUrl(reg.getHealthCheckUrl())
                .healthCheckTimeoutSeconds(reg.getHealthCheckTimeoutSeconds())
                .maxWaitForStartupSeconds(reg.getMaxWaitForStartupSeconds())
                .stopCommand(reg.getStopCommand())
                .build();

            codeDeployRepository.save(entity);
            log.info("Added code deploy registration: {}", reg.getRegistrationId());
        }

        return args -> {};
    }

    @Bean
    public CommandLineRunner checkLocks(AdvisoryLock advisoryLock) {
        advisoryLock.scheduleAdvisoryLockLogger();
        return args -> {};
    }
}
