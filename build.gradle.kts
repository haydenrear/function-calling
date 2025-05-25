plugins {
    id("com.hayden.jpa-persistence")
    id("com.hayden.spring-app")
    id("com.hayden.graphql-data-service")
    id("com.hayden.discovery-app")
    id("com.hayden.messaging")
    id("com.hayden.ai")
    id("com.hayden.docker-compose")
}

group = "com.hayden"
version = "0.0.1-SNAPSHOT"

tasks.register("prepareKotlinBuildScriptModel") {}

dependencies {
    implementation("org.modelmapper:modelmapper:3.0.0")
    implementation(project(":proto"))
    implementation(project(":utilitymodule"))
    implementation(project(":tracing"))
    implementation(project(":commit-diff-model"))
    implementation(project(":jpa-persistence"))
}


tasks.generateJava {
    typeMapping = mutableMapOf(
        Pair("ServerByteArray", "com.hayden.commitdiffmodel.scalar.ByteArray"),
        Pair("Float32Array", "com.hayden.commitdiffmodel.scalar.FloatArray"),
    )
}
