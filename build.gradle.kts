import Com_hayden_docker_gradle.DockerContext
import java.nio.file.Paths

plugins {
    id("com.hayden.jpa-persistence")
    id("com.hayden.spring-app")
    id("com.hayden.graphql-data-service")
    id("com.hayden.discovery-app")
    id("com.hayden.messaging")
    id("com.hayden.ai")
    id("com.hayden.docker-compose")
    id("com.hayden.docker")
}

wrapDocker {
    ctx = arrayOf(
        DockerContext(
            "localhost:5001/function-calling",
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

tasks.register("copyJar") {
    inputs.dir(file(p).resolve("src/main/docker"))
    dependsOn("bootJar")
    delete {
        fileTree(Paths.get(p.toString(),"src/main/docker")) {
            include("**/*.jar")
        }
    }
    copy {
        from(Paths.get(p.toString(), "build/libs"))
        into(Paths.get(p.toString(),"src/main/docker"))
        include("function-calling.jar")
    }
}

if (enableDocker && buildCommitDiffContext) {
    tasks.getByPath("bootJar").finalizedBy("buildDocker")

    tasks.getByPath("bootJar").doLast {
        tasks.getByPath("functionCallingDockerImage").dependsOn("copyJar")
        tasks.getByPath("pushImages").dependsOn("copyJar")
    }

    tasks.register("buildDocker") {
        inputs.dir(file(p).resolve("src/main/docker"))
        dependsOn("bootJar", "copyJar", "functionCallingDockerImage", "pushImages")
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
    implementation(project(":tracing"))
    implementation(project(":commit-diff-model"))
    implementation(project(":commit-diff-context"))
    implementation(project(":jpa-persistence"))
    implementation("org.jsoup:jsoup:1.15.3")
}

tasks.generateJava {
    typeMapping = mutableMapOf(
        Pair("ServerByteArray", "com.hayden.commitdiffmodel.scalar.ByteArray"),
        Pair("Float32Array", "com.hayden.commitdiffmodel.scalar.FloatArray"),
    )
}
