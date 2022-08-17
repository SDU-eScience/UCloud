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
    val ktorVersion = "2.0.2"

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
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
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
                api("io.ktor:ktor-client-websockets:$ktorVersion")
                api("io.ktor:ktor-client-cio:$ktorVersion")
                api("io.ktor:ktor-client-core:$ktorVersion")

                api("org.apache.logging.log4j:log4j-api:2.17.1")
                api("org.apache.logging.log4j:log4j-core:2.17.1")
                api("com.auth0:java-jwt:3.8.3")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        val linuxX64Main by getting {
            dependencies {
            }
        }
        val linuxX64Test by getting {}

        val macosX64Main by getting {
            dependencies {
            }
        }
        val macosX64Test by getting {}

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
extensions.configure<PublishingExtension>("publishing") {
    repositories {
        maven {
            mavenLocal()

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
