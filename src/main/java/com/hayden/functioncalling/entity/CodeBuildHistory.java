package com.hayden.functioncalling.entity;

import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CodeBuildHistory extends JpaHibernateAuditedIded {

    @Column(nullable = false)
    private String buildId;

    @Column(nullable = false)
    private String registrationId;

    @Column
    private String buildCommand;

    @Column
    private String arguments;

    @Column
    private Boolean success;

    @Column
    private Integer exitCode;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column
    private Integer executionTimeMs;

    @Column
    private String sessionId;

    @Column
    private List<String> artifactPaths;

    @Column
    private String artifactOutputDirectory;

    @Column(columnDefinition = "TEXT")
    private String buildLog;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ExecutionType executionType = ExecutionType.PROCESS_BUILDER;
}
