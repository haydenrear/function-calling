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
    implementation("org.springframework.ai:spring-ai-markdown-document-reader")
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")
    implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-tika-document-reader")
    implementation("org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter")
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

tasks.generateJava {
    schemaPaths.add("${projectDir}/src/main/resources/schema")
    packageName = "com.hayden.functioncalling.codegen"
    generateClient = true
    typeMapping = mutableMapOf(
        Pair("ByteArray", "com.hayden.functioncalling.config.ByteArray")
    )
}
