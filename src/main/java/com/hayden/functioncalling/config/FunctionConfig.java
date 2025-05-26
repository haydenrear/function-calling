package com.hayden.functioncalling.config;

import com.hayden.persistence.config.JpaConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan(basePackages = {
        "com.hayden.commitdiffmodel.codegen",
        "com.hayden.commitdiffmodel.scalar"})
@Import({JpaConfig.class})
public class FunctionConfig {
}
