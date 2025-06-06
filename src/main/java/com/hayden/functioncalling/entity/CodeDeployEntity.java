package com.hayden.functioncalling.entity;

import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
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
public class CodeDeployEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String registrationId;

    @Column(nullable = false)
    private String deployCommand;

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
    private List<String> deploySuccessPatterns;

    @Column
    private List<String> deployFailurePatterns;

    @Column
    private List<String> outputRegex;

    @Column
    private String sessionId;

    @Column
    private String healthCheckUrl;

    @Column
    private Integer healthCheckTimeoutSeconds;

    @Column
    private Integer maxWaitForStartupSeconds;

    @Column
    private String stopCommand;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ExecutionType executionType = ExecutionType.PROCESS_BUILDER;
}
