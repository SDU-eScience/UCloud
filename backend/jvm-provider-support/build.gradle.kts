plugins {
    id("org.springframework.boot") version "2.4.3"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.spring") version "1.4.30"
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
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

version = "2021.1.2"
group = "dk.sdu.cloud"

publishing {
    repositories {
        maven {
            mavenLocal()
        }

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/sdu-escience/ucloud")
            credentials {
                username = (project.findProperty("gpr.user") as? String?)
                    ?: System.getenv("GITHUB_USERNAME")
                password = (project.findProperty("gpr.key") as? String?)
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
