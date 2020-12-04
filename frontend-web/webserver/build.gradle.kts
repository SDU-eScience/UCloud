plugins {
    java
    kotlin("jvm") version "1.4.10"
    application
}

application {
    mainClassName = "dk.sdu.cloud.web.MainKt"
}

group = "dk.sdu.cloud"
version = "1.0-SNAPSHOT"

buildscript {
    repositories {
        jcenter()
        mavenCentral()
        maven { setUrl("https://plugins.gradle.org/m2/") }
    }
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    val ktorVersion = "1.4.0"
    implementation(kotlin("stdlib"))
    testCompile("junit", "junit", "4.12")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_11
}
tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
    }
}
