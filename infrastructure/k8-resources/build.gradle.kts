plugins {
    kotlin("jvm") version "1.3.61"
    `maven-publish`
    application
}

group = "dk.sdu.cloud"
version = "0.1.0"

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
}

task<JavaExec>("runApp") {
    classpath = sourceSets.main.get().runtimeClasspath
    main = "dk.sdu.cloud.k8.LauncherKt"
    standardInput = System.`in`
    workingDir = File(properties["cwd"]?.toString() ?: ".")
}

publishing {
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
