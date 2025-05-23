plugins {
    id("com.hayden.jpa-persistence")
    id("com.hayden.spring-app")
    id("com.hayden.graphql-data-service")
    id("com.hayden.discovery-app")
    id("com.hayden.messaging")
    id("com.hayden.docker-compose")
}

group = "com.hayden"
version = "0.0.1-SNAPSHOT"

extra["springAiVersion"] = "1.0.0-M4"

dependencies {
    implementation(project(":proto"))
    implementation(project(":utilitymodule"))
    implementation(project(":tracing"))
    implementation(project(":commit-diff-model"))
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.register("prepareKotlinBuildScriptModel")

