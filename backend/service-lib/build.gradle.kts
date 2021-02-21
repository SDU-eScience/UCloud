plugins {
    kotlin("multiplatform") version "1.4.30"
    kotlin("plugin.serialization") version "1.4.30"
}

repositories {
    jcenter()
    mavenCentral()
}

kotlin {
    val jacksonVersion = "2.10.0.pr3"
    val ktorVersion = "1.4.0"
    val jasyncVersion = "1.1.3"

    jvm {
        withJava()
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = "11"
            }
        }

        val test by compilations.getting {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api("io.ktor:ktor-client-core:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmMain by getting {
            dependencies {
                api("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
                api("io.ktor:ktor-server-core:$ktorVersion")
                api("io.ktor:ktor-server-netty:$ktorVersion")
                api("io.ktor:ktor-server-host-common:$ktorVersion")
                api("io.ktor:ktor-websockets:$ktorVersion")
                api("org.jetbrains:annotations:16.0.2")

                api("io.ktor:ktor-client-okhttp:$ktorVersion")
                api("io.ktor:ktor-client-websockets:$ktorVersion")
                api("io.ktor:ktor-client-cio:$ktorVersion")

                api("org.apache.logging.log4j:log4j-api:2.12.0")
                api("org.apache.logging.log4j:log4j-slf4j-impl:2.12.0")
                api("org.apache.logging.log4j:log4j-core:2.12.0")
                api("com.auth0:java-jwt:3.8.3")

                api("org.postgresql:postgresql:42.2.5")
                api("org.flywaydb:flyway-core:5.2.4")

                api("com.github.jasync-sql:jasync-common:$jasyncVersion")
                api("com.github.jasync-sql:jasync-postgresql:$jasyncVersion")
                api("io.lettuce:lettuce-core:5.1.6.RELEASE")
                api("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.5.0")
                api("com.google.guava:guava:27.0.1-jre")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        all {
            languageSettings.enableLanguageFeature("InlineClasses")
            languageSettings.progressiveMode = true
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}
