import Com_hayden_docker_gradle.DockerContext
import java.nio.file.Paths

plugins {
    id("com.hayden.jpa-persistence")
    id("com.hayden.spring-app")
    id("com.hayden.graphql-data-service")
    id("com.hayden.messaging")
    id("com.hayden.ai")
    id("com.hayden.docker-compose")
    id("com.hayden.docker")
}

wrapDocker {
    ctx = arrayOf(
        DockerContext(
            "localhost:5005/function-calling",
            "${project.projectDir}/src/main/docker",
            "functionCalling"
        )
    )
}

tasks.bootJar {
    archiveFileName = "function-calling.jar"
    enabled = true
}

val enableDocker = project.property("enable-docker")?.toString()?.toBoolean()?.or(false) ?: false

val buildCommitDiffContext = project.property("build-function-calling")?.toString()?.toBoolean()?.or(false) ?: false

var p = layout.projectDirectory

if (enableDocker && buildCommitDiffContext) {
    tasks.getByPath("bootJar").finalizedBy("buildDocker")

    tasks.getByPath("bootJar").doLast {
        tasks.getByPath("functionCallingDockerImage")
            .dependsOn(project(":runner_code").tasks.getByName("runnerTask"), "copyJar")
    }

    tasks.register("buildDocker") {
        inputs.dir(file(p).resolve("src/main/docker"))
        dependsOn("bootJar", "copyJar", "functionCallingDockerImage")
        doLast {
            delete(fileTree(Paths.get(p.toString(), "src/main/docker")) {
                include("**/*.jar")
            })
        }
    }
}

group = "com.hayden"
version = "0.0.1-SNAPSHOT"

tasks.register("prepareKotlinBuildScriptModel") {}

dependencies {
    implementation("org.modelmapper:modelmapper:3.0.0")
    implementation(project(":proto"))
    implementation(project(":utilitymodule"))
    implementation(project(":runner_code"))
    implementation(project(":tracing"))
    implementation(project(":commit-diff-model"))
    implementation(project(":commit-diff-context"))
    implementation(project(":jpa-persistence"))
    implementation("org.jsoup:jsoup:1.15.3")
}

tasks.generateJava {
    dependsOn(project(":commit-diff-model").tasks.named("generateJava"))
    typeMapping = mutableMapOf(
        Pair("ServerByteArray", "com.hayden.commitdiffmodel.scalar.ByteArray"),
        Pair("Float32Array", "com.hayden.commitdiffmodel.scalar.FloatArray"),
    )
}
