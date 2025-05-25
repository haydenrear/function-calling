package com.hayden.functioncalling.entity;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "code_execution_registrations")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CodeExecutionEntity extends JpaHibernateAuditedIded {
    
    @Column(nullable = false, unique = true)
    private String registrationId;
    
    @Column(nullable = false)
    private String command;
    
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
    private List<String> reportingPaths;

    @Column
    private List<String> outputRegex;
}