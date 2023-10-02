import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    application
    id("org.graalvm.buildtools.native") version "0.9.13"
    kotlin("plugin.serialization") version "1.9.10"
}

group = "dk.sdu.cloud"
version = "2023.3.7-accounting2-1"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://mvn.cloud.sdu.dk")
}

dependencies {
    run {
        val version = "2023.4.0-dev.40"

        fun ucloud(module: String) = implementation("dk.sdu.cloud:$module:$version")

        ucloud("file-orchestrator-service-api")
        ucloud("app-orchestrator-service-api")
        ucloud("service-lib-lib")

        implementation("org.cliffc.high_scale_lib:cliff-utils:2023.4.0-dev.25")
    }

    run {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    }

    run {
        val ktorVersion = "2.2.3"
        fun ktor(module: String) = implementation("io.ktor:ktor-$module:$ktorVersion")

        ktor("client-websockets")
        ktor("client-cio")
        ktor("client-core")

        ktor("server-core")
        ktor("server-cio")
        ktor("server-websockets")
        ktor("server-cors")
        ktor("server-host-common")

        ktor("websockets")
    }

    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("com.auth0:java-jwt:4.3.0")
    implementation("com.charleskorn.kaml:kaml:0.47.0")

    implementation(project(":embedded-postgres"))
    implementation("org.postgresql:postgresql:42.5.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
    compilerOptions.freeCompilerArgs.add("-Xcontext-receivers")
}

application {
    mainClass.set("dk.sdu.cloud.MainKt")
}

graalvmNative {
    // See this section for how to run the agent to detect reflection:
    // https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#agent-support

    binaries {
        named("main") {
            fallback.set(false)
            verbose.set(true)

            buildArgs.add("--initialize-at-build-time=io.ktor,kotlin,kotlinx.coroutines,ch.qos.logback,org.slf4j")

            buildArgs.add("-H:+InstallExitHandlers")
            buildArgs.add("-H:+ReportUnsupportedElementsAtRuntime")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:MaxDuplicationFactor=2.0")

            buildArgs.add("-R:MaxHeapSize=1g")
            buildArgs.add("-R:MinHeapSize=128m")
            buildArgs.add("-R:MaxNewSize=64m")
            buildArgs.add("--trace-class-initialization=org.slf4j.LoggerFactory")
        }
    }
}

tasks.create("buildDebug") {
    dependsOn(tasks.named("installDist"))
}

tasks.create("linkDebugExecutableNative") {
    dependsOn(tasks.named("buildDebug"))
}
