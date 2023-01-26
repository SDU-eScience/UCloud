import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
}

group = "dk.sdu.cloud"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://mvn.cloud.sdu.dk")
}

dependencies {
     run {
        val version = "2022.3.3"
        fun ucloud(module: String) = implementation("dk.sdu.cloud:$module:$version")

        ucloud("file-orchestrator-service-api")
        ucloud("app-orchestrator-service-api")
        ucloud("service-lib-lib")
    }

    implementation("org.slf4j:slf4j-nop:2.0.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
