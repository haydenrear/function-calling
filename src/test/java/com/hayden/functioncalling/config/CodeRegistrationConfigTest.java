package com.hayden.functioncalling.config;

import com.hayden.functioncalling.entity.CodeBuildEntity;
import com.hayden.functioncalling.entity.CodeDeployEntity;
import com.hayden.functioncalling.entity.TestExecutionEntity;
import com.hayden.functioncalling.repository.CodeBuildRepository;
import com.hayden.functioncalling.repository.CodeDeployRepository;
import com.hayden.functioncalling.repository.TestExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
public class CodeRegistrationConfigTest {

    @Autowired
    private TestExecutionRepository testExecutionRepository;

    @Autowired
    private CodeBuildRepository codeBuildRepository;

    @Autowired
    private CodeDeployRepository codeDeployRepository;

    @Test
    void testTestExecutionRegistrationsLoaded() {
        // Verify that test execution registrations from application-test.yml are loaded
        List<TestExecutionEntity> entities = testExecutionRepository.findByEnabledTrue();

        assertThat(entities).isNotEmpty();
        assertThat(entities.size()).isGreaterThanOrEqualTo(3);

        // Check specific registrations
        Optional<TestExecutionEntity> echoEntity = testExecutionRepository.findByRegistrationId("echo");
        assertThat(echoEntity).isPresent();
        assertThat(echoEntity.get().getCommand()).isEqualTo("echo");
        assertThat(echoEntity.get().getArguments()).isEqualTo("Hello, World!");
        assertThat(echoEntity.get().getEnabled()).isTrue();

        Optional<TestExecutionEntity> lsEntity = testExecutionRepository.findByRegistrationId("ls");
        assertThat(lsEntity).isPresent();
        assertThat(lsEntity.get().getCommand()).isEqualTo("ls");
        assertThat(lsEntity.get().getArguments()).isEqualTo("-la");
        assertThat(lsEntity.get().getEnabled()).isTrue();

        Optional<TestExecutionEntity> pwdEntity = testExecutionRepository.findByRegistrationId("pwd");
        assertThat(pwdEntity).isPresent();
        assertThat(pwdEntity.get().getCommand()).isEqualTo("pwd");
        assertThat(pwdEntity.get().getEnabled()).isTrue();
    }

    @Test
    void testCodeBuildRegistrationsLoaded() {
        // Verify that build registrations from application-test.yml are loaded
        List<CodeBuildEntity> entities = codeBuildRepository.findAll();

        assertThat(entities).isNotEmpty();
        assertThat(entities.size()).isGreaterThanOrEqualTo(4);

        // Check successful build registration
        Optional<CodeBuildEntity> successEntity = codeBuildRepository.findByRegistrationId("test-build-success");
        assertThat(successEntity).isPresent();
        assertThat(successEntity.get().getBuildCommand()).isEqualTo("bash");
        assertThat(successEntity.get().getArguments()).isEqualTo("src/test/resources/scripts/build.sh");
        assertThat(successEntity.get().getEnabled()).isTrue();
        assertThat(successEntity.get().getTimeoutSeconds()).isEqualTo(30);
        assertThat(successEntity.get().getArtifactPaths().stream().anyMatch(s -> s.endsWith("target/myapp-1.0.0.jar"))).isTrue();
        assertThat(successEntity.get().getBuildSuccessPatterns()).contains("Build completed successfully!");
        assertThat(successEntity.get().getBuildFailurePatterns()).contains("Build failed with errors!");

        // Check failing build registration
        Optional<CodeBuildEntity> failEntity = codeBuildRepository.findByRegistrationId("test-build-fail");
        assertThat(failEntity).isPresent();
        assertThat(failEntity.get().getBuildCommand()).isEqualTo("bash");
        assertThat(failEntity.get().getArguments()).isEqualTo("src/test/resources/scripts/build-fail.sh");
        assertThat(failEntity.get().getEnabled()).isTrue();

        // Check timeout build registration
        Optional<CodeBuildEntity> timeoutEntity = codeBuildRepository.findByRegistrationId("test-build-timeout");
        assertThat(timeoutEntity).isPresent();
        assertThat(timeoutEntity.get().getBuildCommand()).isEqualTo("sleep");
        assertThat(timeoutEntity.get().getArguments()).isEqualTo("60");
        assertThat(timeoutEntity.get().getTimeoutSeconds()).isEqualTo(2);
        assertThat(timeoutEntity.get().getEnabled()).isTrue();

        // Check disabled build registration
        Optional<CodeBuildEntity> disabledEntity = codeBuildRepository.findByRegistrationId("test-build-disabled");
        assertThat(disabledEntity).isPresent();
        assertThat(disabledEntity.get().getEnabled()).isFalse();
    }

    @Test
    void testCodeDeployRegistrationsLoaded() {
        // Verify that deploy registrations from application-test.yml are loaded
        List<CodeDeployEntity> entities = codeDeployRepository.findAll();

        assertThat(entities).isNotEmpty();
        assertThat(entities.size()).isGreaterThanOrEqualTo(4);

        // Check successful deploy registration with health check
        Optional<CodeDeployEntity> successEntity = codeDeployRepository.findByRegistrationId("test-deploy-success");
        assertThat(successEntity).isPresent();
        assertThat(successEntity.get().getDeployCommand()).isEqualTo("bash");
        assertThat(successEntity.get().getArguments()).isEqualTo("src/test/resources/scripts/deploy.sh");
        assertThat(successEntity.get().getEnabled()).isTrue();
        assertThat(successEntity.get().getTimeoutSeconds()).isEqualTo(30);
        assertThat(successEntity.get().getDeploySuccessPatterns()).contains("Deployment completed successfully!");
        assertThat(successEntity.get().getDeployFailurePatterns()).contains("Deployment failed!");
        assertThat(successEntity.get().getHealthCheckUrl()).isEqualTo("http://localhost:8080/health");
        assertThat(successEntity.get().getHealthCheckTimeoutSeconds()).isEqualTo(5);
        assertThat(successEntity.get().getMaxWaitForStartupSeconds()).isEqualTo(10);
        assertThat(successEntity.get().getStopCommand()).isEqualTo("bash src/test/resources/scripts/stop-deploy.sh");

        // Check deploy registration without health check
        Optional<CodeDeployEntity> noHealthEntity = codeDeployRepository.findByRegistrationId("test-deploy-no-health");
        assertThat(noHealthEntity).isPresent();
        assertThat(noHealthEntity.get().getDeployCommand()).isEqualTo("bash");
        assertThat(noHealthEntity.get().getArguments()).isEqualTo("src/test/resources/scripts/deploy.sh");
        assertThat(noHealthEntity.get().getEnabled()).isTrue();
        assertThat(noHealthEntity.get().getHealthCheckUrl()).isNull();

        // Check timeout deploy registration
        Optional<CodeDeployEntity> timeoutEntity = codeDeployRepository.findByRegistrationId("test-deploy-timeout");
        assertThat(timeoutEntity).isPresent();
        assertThat(timeoutEntity.get().getDeployCommand()).isEqualTo("sleep");
        assertThat(timeoutEntity.get().getArguments()).isEqualTo("60");
        assertThat(timeoutEntity.get().getTimeoutSeconds()).isEqualTo(2);
        assertThat(timeoutEntity.get().getMaxWaitForStartupSeconds()).isEqualTo(1);
        assertThat(timeoutEntity.get().getEnabled()).isTrue();

        // Check disabled deploy registration
        Optional<CodeDeployEntity> disabledEntity = codeDeployRepository.findByRegistrationId("test-deploy-disabled");
        assertThat(disabledEntity).isPresent();
        assertThat(disabledEntity.get().getEnabled()).isFalse();
    }

    @Test
    void testEnabledRegistrationsOnly() {
        // Test that only enabled registrations are returned by the enabled filter
        List<TestExecutionEntity> enabledTestEntities = testExecutionRepository.findByEnabledTrue();
        assertThat(enabledTestEntities).allMatch(TestExecutionEntity::getEnabled);

        List<CodeBuildEntity> enabledBuildEntities = codeBuildRepository.findByEnabledTrue();
        assertThat(enabledBuildEntities).allMatch(CodeBuildEntity::getEnabled);

        List<CodeDeployEntity> enabledDeployEntities = codeDeployRepository.findByEnabledTrue();
        assertThat(enabledDeployEntities).allMatch(CodeDeployEntity::getEnabled);
    }

    @Test
    void testRegistrationIdUniqueness() {
        // Verify that registration IDs are unique
        List<TestExecutionEntity> testEntities = testExecutionRepository.findAll();
        long uniqueTestIds = testEntities.stream()
                .map(TestExecutionEntity::getRegistrationId)
                .distinct()
                .count();
        assertThat(uniqueTestIds).isEqualTo(testEntities.size());

        List<CodeBuildEntity> buildEntities = codeBuildRepository.findAll();
        long uniqueBuildIds = buildEntities.stream()
                .map(CodeBuildEntity::getRegistrationId)
                .distinct()
                .count();
        assertThat(uniqueBuildIds).isEqualTo(buildEntities.size());

        List<CodeDeployEntity> deployEntities = codeDeployRepository.findAll();
        long uniqueDeployIds = deployEntities.stream()
                .map(CodeDeployEntity::getRegistrationId)
                .distinct()
                .count();
        assertThat(uniqueDeployIds).isEqualTo(deployEntities.size());
    }
}
