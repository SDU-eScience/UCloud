import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
    application
}

group = "dk.sdu.cloud.debugger"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    run {
        val ktorVersion = "2.2.1"
        fun ktor(module: String) {
            api("io.ktor:ktor-$module:$ktorVersion")
        }

        ktor("server-core")
        ktor("server-cio")
        ktor("server-websockets")
        ktor("server-cors")
        ktor("server-host-common")
        ktor("server-forwarded-header")
        ktor("server-default-headers")
        ktor("server-call-logging")
        ktor("server-caching-headers")

        ktor("websockets")
    }

    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}
