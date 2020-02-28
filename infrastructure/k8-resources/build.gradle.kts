import java.net.URI

plugins {
    kotlin("jvm") version "1.3.61"
    `maven-publish`
    application
}

group = "dk.sdu.cloud"
version = "0.1.2"

repositories {
    mavenCentral()
}

application {
    mainClassName = "dk.sdu.cloud.k8.LauncherKt"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("script-util"))
    implementation(kotlin("script-runtime"))
    implementation(kotlin("compiler-embeddable"))
    implementation(kotlin("scripting-compiler-embeddable"))
    implementation("net.java.dev.jna:jna:5.5.0")
    implementation("io.fabric8:kubernetes-client:4.6.4")
    implementation("org.slf4j:slf4j-simple:1.7.25")

    val jacksonVersion = "2.10.0.pr3"
    implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")

    val ktorVersion = "1.3.0"
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
}

task<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath
    main = "dk.sdu.cloud.k8.LauncherKt"
    standardInput = System.`in`
    workingDir = File(properties["cwd"]?.toString() ?: ".")
}

publishing {
    val username = (System.getenv("ESCIENCE_MVN_USER") ?: properties["eScienceCloudUser"]).toString()
    val password = (System.getenv("ESCIENCE_MVN_PASSWORD") ?: properties["eScienceCloudPassword"]).toString()

    repositories {
        maven {
            var resolvedUrl = "https://archiva.dev.cloud.sdu.dk/repository/"
            resolvedUrl +=
                if (project.version.toString().endsWith("-SNAPSHOT")) "snapshots"
                else "internal"

            url = URI(resolvedUrl)
            credentials {
                this.username = username
                this.password = password
            }
        }
    }

    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}
