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

                run {
                    val ktorVersion = "2.0.2"
                    fun ktor(module: String) {
                        api("io.ktor:ktor-$module:$ktorVersion")
                    }

                    ktor("client-websockets")
                    ktor("client-cio")
                    ktor("client-core")

                    ktor("server-core")
                    ktor("server-netty")
                    ktor("server-websockets")
                    ktor("server-cors")
                    ktor("server-host-common")
                    ktor("server-forwarded-header")
                    ktor("server-default-headers")
                    ktor("server-call-logging")
                    ktor("server-caching-headers")

                    ktor("websockets")
                }

                api("org.jetbrains:annotations:16.0.2")
                api(kotlin("reflect"))

                api("org.apache.logging.log4j:log4j-slf4j-impl:2.17.1")
                api("com.auth0:java-jwt:3.8.3")

                api("org.postgresql:postgresql:42.2.5")
                api("org.flywaydb:flyway-core:5.2.4")

                api("com.github.jasync-sql:jasync-common:$jasyncVersion")
                api("com.github.jasync-sql:jasync-postgresql:$jasyncVersion")
                api("io.lettuce:lettuce-core:5.1.6.RELEASE")
                api("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.15.0")
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
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}
