plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
}

repositories {
    jcenter()
    mavenCentral()
}

kotlin {
    val jacksonVersion = "2.10.4"
    val ktorVersion = "1.5.2"
    val jasyncVersion = "1.1.3"

    macosX64()
    linuxX64()

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
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
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
                api("io.ktor:ktor-client-okhttp:$ktorVersion")
                api("io.ktor:ktor-client-websockets:$ktorVersion")
                api("io.ktor:ktor-client-cio:$ktorVersion")

                api("org.apache.logging.log4j:log4j-api:2.12.0")
                api("org.apache.logging.log4j:log4j-core:2.12.0")
                implementation(kotlin("reflect"))
                implementation("com.google.guava:guava:27.0.1-jre")
                api("com.auth0:java-jwt:3.8.3")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        val linuxX64Main by getting {
            dependencies {
                api("io.ktor:ktor-client-curl:$ktorVersion")
                api("io.ktor:ktor-client-websockets:$ktorVersion")
                api("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        val linuxX64Test by getting {}

        val macosX64Main by getting {
            dependencies {
                api("io.ktor:ktor-client-curl:$ktorVersion")
                api("io.ktor:ktor-client-websockets:$ktorVersion")
                api("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        val macosX64Test by getting {}

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
extensions.configure<PublishingExtension>("publishing") {
    repositories {
        maven {
            mavenLocal()

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

    publications {
        all {
            if (this is MavenPublication) {
                this.groupId = "dk.sdu.cloud"
                val metadata = artifactId.substringAfterLast("-")
                this.artifactId = "service-lib" + if (metadata == "api") "" else "-$metadata"
            }
        }
    }
}
