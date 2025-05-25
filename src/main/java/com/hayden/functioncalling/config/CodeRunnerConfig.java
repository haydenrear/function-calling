package com.hayden.functioncalling.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.commitdiffmodel.convert.CommitDiffContextMapper;
import com.hayden.functioncalling.entity.CodeExecutionEntity;
import com.hayden.functioncalling.repository.CodeExecutionRepository;
import com.querydsl.core.annotations.Config;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class CodeRunnerConfig {

    @Bean
    public CommitDiffContextMapper mapper() {
        return new CommitDiffContextMapper(new ModelMapper());
    }

    @Bean
    public CommandLineRunner add(CodeExecutionRepository codeExecutionRepository, CodeRunnerConfigProps registrations) {
        log.info("Initializing code runner registrations from configuration: {}", registrations.getRegistrations());
        for (CodeRunnerConfigProps.Registration reg : registrations.getRegistrations()) {
            if (codeExecutionRepository.existsByRegistrationId(reg.getId())) {
                log.info("Registration with ID {} already exists, skipping", reg.getId());
                continue;
            }

            CodeExecutionEntity entity = CodeExecutionEntity.builder()
                    .registrationId(reg.getId())
                    .command(reg.getCommand())
                    .workingDirectory(reg.getWorkingDirectory())
                    .description(reg.getDescription())
                    .arguments(reg.getArguments())
                    .timeoutSeconds(reg.getTimeoutSeconds())
                    .enabled(reg.isEnabled())
                    .build();

            codeExecutionRepository.save(entity);
            log.info("Added code execution registration: {}", reg.getId());
        }

        return args -> {};

    }

}
