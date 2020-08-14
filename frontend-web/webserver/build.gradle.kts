plugins {
    java
    kotlin("jvm") version "1.3.72"
    application
}

application {
    mainClassName = "dk.sdu.cloud.web.MainKt"
}

group = "dk.sdu.cloud"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testCompile("junit", "junit", "4.12")
    compile("io.ktor:ktor-server-core:1.2.3")
    compile("io.ktor:ktor-server-netty:1.2.3")
    compile("io.ktor:ktor-server-host-common:1.2.3")
    compile("io.ktor:ktor-websockets:1.2.3")
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