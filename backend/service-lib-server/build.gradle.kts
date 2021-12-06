plugins {
    kotlin("multiplatform")
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
    val jacksonVersion = "2.10.0.pr3"
    val ktorVersion = "1.6.2-native-mm-eap-196"
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
        val jvmMain by getting {
            dependencies {
                api(project(":service-lib"))
                api("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
                api("io.ktor:ktor-server-core:$ktorVersion")
                api("io.ktor:ktor-server-netty:$ktorVersion")
                api("io.ktor:ktor-server-host-common:$ktorVersion")
                api("io.ktor:ktor-websockets:$ktorVersion")
                api("org.jetbrains:annotations:16.0.2")
                api("org.jetbrains.kotlin:kotlin-reflect:1.5.31")

                api("org.apache.logging.log4j:log4j-slf4j-impl:2.12.0")
                api("com.auth0:java-jwt:3.8.3")

                api("org.postgresql:postgresql:42.2.5")
                api("org.flywaydb:flyway-core:5.2.4")

                api("com.github.jasync-sql:jasync-common:$jasyncVersion")
                api("com.github.jasync-sql:jasync-postgresql:$jasyncVersion")
                api("io.lettuce:lettuce-core:5.1.6.RELEASE")
                api("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.11.1")
                api("com.google.guava:guava:27.0.1-jre")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
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
