package com.hayden.functioncalling.model_server;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.zookeeper.ZookeeperAutoConfiguration;
import org.springframework.cloud.zookeeper.discovery.ZookeeperDiscoveryAutoConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;


@SpringBootTest
@ImportAutoConfiguration(exclude = {ZookeeperAutoConfiguration.class, ZookeeperDiscoveryAutoConfiguration.class})
@EnableConfigurationProperties
@ExtendWith(SpringExtension.class)
@ActiveProfiles("testjpa")
public class PostgresMLEmbeddingModelTest {


//    @Test
    public void doTestPostgresEmbeddingModel() {
    }

}
