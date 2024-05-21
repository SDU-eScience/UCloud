import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("maven-publish")
}

repositories {
    jcenter()
    mavenCentral()
    maven { setUrl("https://maven.pkg.jetbrains.space/public/p/kotlinx-coroutines/maven/") }
    maven { setUrl("https://maven.pkg.jetbrains.space/public/p/ktor/eap/") }
}

kotlin {
    jvmToolchain(20)

    sourceSets {
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
            languageSettings.progressiveMode = true
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

dependencies {
    val jacksonVersion = "2.15.0"
    api(project(":service-lib"))
    api("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")

    run {
        val ktorVersion = "2.3.9"
        fun ktor(module: String) {
            api("io.ktor:ktor-$module:$ktorVersion")
        }

        ktor("client-websockets")
        ktor("client-cio")
        ktor("client-core")

        ktor("server-core")
        ktor("server-cio")
        ktor("server-websockets")
        ktor("server-cors")
        ktor("server-host-common")
        ktor("server-forwarded-header")
        ktor("server-default-headers")
        ktor("server-call-logging")
        ktor("server-caching-headers")
        ktor("server-compression")

        ktor("websockets")
    }

    api("org.jetbrains:annotations:16.0.2")
    api(kotlin("reflect"))

    api("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    api("com.auth0:java-jwt:3.19.4")

    api("org.postgresql:postgresql:42.2.27")
    api("org.flywaydb:flyway-core:5.2.4")

    val jasyncVersion = "2.1.24"
    api("com.github.jasync-sql:jasync-common:$jasyncVersion")
    api("com.github.jasync-sql:jasync-postgresql:$jasyncVersion")
    api("io.lettuce:lettuce-core:5.1.6.RELEASE")
    api("co.elastic.clients:elasticsearch-java:8.11.0")

    api("com.google.guava:guava:31.1-jre")

    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4")

}
