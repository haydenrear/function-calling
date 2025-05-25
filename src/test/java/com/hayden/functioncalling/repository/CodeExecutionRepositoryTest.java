package com.hayden.functioncalling.repository;

import com.hayden.functioncalling.entity.CodeExecutionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test")
@Transactional
public class CodeExecutionRepositoryTest {

    @Autowired
    private CodeExecutionRepository repository;

    private String registrationId;

    @BeforeEach
    void setUp() {
        registrationId = UUID.randomUUID().toString();

        CodeExecutionEntity entity = CodeExecutionEntity.builder()
                .registrationId(registrationId)
                .command("echo")
                .arguments("Hello World")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(true)
                .timeoutSeconds(10)
                .description("Test command")
                .build();

        repository.save(entity);

        // Add a disabled entity
        CodeExecutionEntity disabledEntity = CodeExecutionEntity.builder()
                .registrationId(UUID.randomUUID().toString())
                .command("ls")
                .arguments("-la")
                .workingDirectory(System.getProperty("user.dir"))
                .enabled(false)
                .timeoutSeconds(5)
                .description("Disabled command")
                .build();

        repository.save(disabledEntity);
    }

    @Test
    void testFindByRegistrationId() {
        Optional<CodeExecutionEntity> found = repository.findByRegistrationId(registrationId);
        assertThat(found).isPresent();
        assertThat(found.get().getRegistrationId()).isEqualTo(registrationId);
    }

    @Test
    void testFindByEnabledTrue() {
        List<CodeExecutionEntity> enabledEntities = repository.findByEnabledTrue();
        assertThat(enabledEntities).isNotEmpty();
        assertThat(enabledEntities).allMatch(entity -> entity.getEnabled());
    }

    @Test
    void testExistsByRegistrationId() {
        boolean exists = repository.existsByRegistrationId(registrationId);
        assertThat(exists).isTrue();
        
        boolean nonExistent = repository.existsByRegistrationId("non-existent-id");
        assertThat(nonExistent).isFalse();
    }

    @Test
    void testSaveAndDelete() {
        // Create a new entity
        String newId = UUID.randomUUID().toString();
        CodeExecutionEntity newEntity = CodeExecutionEntity.builder()
                .registrationId(newId)
                .command("grep")
                .arguments("pattern file")
                .workingDirectory("/tmp")
                .enabled(true)
                .timeoutSeconds(30)
                .description("Search command")
                .build();

        // Save it
        CodeExecutionEntity savedEntity = repository.save(newEntity);
        assertThat(savedEntity.getRegistrationId()).isNotNull();
        
        // Verify it can be found
        assertThat(repository.findByRegistrationId(newId)).isPresent();
        
        // Delete it
        repository.delete(savedEntity);
        
        // Verify it's gone
        assertThat(repository.findByRegistrationId(newId)).isEmpty();
    }

    @Test
    void testFindAll() {
        List<CodeExecutionEntity> allEntities = repository.findAll();
        assertThat(allEntities.size()).isGreaterThanOrEqualTo(2); // At least our two test entities
    }

    @Test
    void testUpdateEntity() {
        // Get the entity
        CodeExecutionEntity entity = repository.findByRegistrationId(registrationId).orElseThrow();
        
        // Update it
        entity.setCommand("updated-command");
        entity.setArguments("updated-args");
        entity.setTimeoutSeconds(60);
        
        // Save it
        repository.save(entity);
        
        // Retrieve it again and verify updates
        CodeExecutionEntity updated = repository.findByRegistrationId(registrationId).orElseThrow();
        assertThat(updated.getCommand()).isEqualTo("updated-command");
        assertThat(updated.getArguments()).isEqualTo("updated-args");
        assertThat(updated.getTimeoutSeconds()).isEqualTo(60);
    }
}