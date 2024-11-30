package com.hayden.functioncalling.config;

import com.hayden.utilitymodule.ctx.PreSetConfigurationProperties;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "model-server")
@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
@PreSetConfigurationProperties
public class ModelServerConfigProperties {

    List<ModelDescriptor> models = new ArrayList<>();

}
