package com.hayden.functioncalling.entity;

import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import com.hayden.persistence.models.AuditedEntity;
import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import java.util.List;
import lombok.*;

@Entity
@Table
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CodeBuildEntity extends AuditedEntity<String> {

    @Id
    @org.springframework.data.annotation.Id
    private String registrationId;

    @Column(nullable = false)
    private String buildCommand;

    @Column
    private String workingDirectory;

    @Column
    private String description;

    @Column
    private String arguments;

    @Column
    private Integer timeoutSeconds;

    @Column(nullable = false)
    private Boolean enabled;

    @Column
    private List<String> artifactPaths;

    @Column
    private List<String> outputRegex;

    @Column
    private String sessionId;

    @Column
    private String artifactOutputDirectory;

    @Column
    private List<String> buildSuccessPatterns;

    @Column
    private List<String> buildFailurePatterns;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ExecutionType executionType = ExecutionType.PROCESS_BUILDER;

    @Override
    public String equalsAndHashCodeId() {
        return registrationId;
    }
}
