package com.hayden.functioncalling.entity;

import com.hayden.commitdiffmodel.codegen.types.ExecutionType;
import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TestExecutionHistory extends JpaHibernateAuditedIded {
    
    @Column(nullable = false)
    private String executionId;
    
    @Column(nullable = false)
    private String registrationId;
    
    @Column
    private String command;
    
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


    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ExecutionType executionType = ExecutionType.PROCESS_BUILDER;


}