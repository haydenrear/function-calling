package com.hayden.functioncalling.entity;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "code_execution_history")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CodeExecutionHistory extends JpaHibernateAuditedIded {
    
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
}