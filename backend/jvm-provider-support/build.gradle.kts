plugins {
    id("org.springframework.boot") version "2.4.3"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.spring") version "1.5.31"
    id("maven-publish")
}

repositories {
    jcenter()
    mavenCentral()
}

kotlin {
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
                val jacksonVersion = "2.11.4"
                api(project(":integration-module-support"))
                implementation("org.springframework.boot:spring-boot-starter-websocket")
                implementation("org.springframework:spring-web:5.3.4")
                implementation("org.springframework.boot:spring-boot-starter-web")

                // Spring boot bundles its own version which isn't compatible with what we need
                api("com.squareup.okhttp3:okhttp:4.6.0")

                api("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
                api("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
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

version = rootProject.file("./version.txt").readText().trim()
group = "dk.sdu.cloud"

publishing {
    repositories {
        maven {
            mavenLocal()
        }

        maven {
            name = "UCloudMaven"
            url = uri("https://mvn.cloud.sdu.dk/releases")
            credentials {
                username = (project.findProperty("ucloud.mvn.username") as? String?)
                    ?: System.getenv("UCLOUD_MVN_USERNAME")
                password = (project.findProperty("ucloud.mvn.token") as? String?)
                    ?: System.getenv("UCLOUD_MVN_TOKEN")
            }
        }
    }
}
