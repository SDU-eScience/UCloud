import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    application
    id("org.graalvm.buildtools.native") version "0.9.13"
    kotlin("plugin.serialization") version "1.7.20"
}

group = "dk.sdu.cloud"
version = "2022.3.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://mvn.cloud.sdu.dk")
}

dependencies {
    run {
        val version = "2022.2.70"
        fun ucloud(module: String) = implementation("dk.sdu.cloud:$module:$version")

        ucloud("file-orchestrator-service-api")
        ucloud("app-orchestrator-service-api")
        ucloud("service-lib-lib")
    }

    run {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
        implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    }

    run {
        val ktorVersion = "2.0.2"
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

    implementation("ch.qos.logback:logback-classic:1.4.1")
    implementation("com.auth0:java-jwt:4.0.0")
    implementation("org.xerial:sqlite-jdbc:3.36.0.3")
    implementation("com.charleskorn.kaml:kaml:0.47.0")

    implementation(project(":embedded-postgres"))
    implementation("org.postgresql:postgresql:42.5.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
    kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
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

            buildArgs.add("--initialize-at-build-time=io.ktor,kotlin,kotlinx.coroutines")

            buildArgs.add("-H:+InstallExitHandlers")
            buildArgs.add("-H:+ReportUnsupportedElementsAtRuntime")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:MaxDuplicationFactor=2.0")

            buildArgs.add("-R:MaxHeapSize=1g")
            buildArgs.add("-R:MinHeapSize=128m")
            buildArgs.add("-R:MaxNewSize=64m")
        }
    }
}

tasks.create("buildDebug") {
    dependsOn(tasks.named("installDist"))
}

tasks.create("linkDebugExecutableNative") {
    dependsOn(tasks.named("buildDebug"))
}
