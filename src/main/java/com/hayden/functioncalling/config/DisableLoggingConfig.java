package com.hayden.functioncalling.config;

import com.hayden.tracing.config.DisableTelemetryLoggingConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Import(DisableTelemetryLoggingConfig.class)
@Profile("!telemetry-logging")
public class DisableLoggingConfig { }
