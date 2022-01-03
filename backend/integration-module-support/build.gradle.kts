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
    linuxX64()
    macosX64()

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
                api(project(":service-lib"))

                rootProject.childProjects.values
                    .filter { it.name.endsWith("-service") }
                    .forEach { p ->
                        val hasApiProject = rootProject.subprojects
                            .find { it.name == p.name }!!.subprojects
                            .any { it.name == "api" }
                        if (hasApiProject) api(project(":" + p.name + ":api"))
                    }
            }
        }

        val jvmMain by getting {}
        val linuxX64Main by getting {}
        val macosX64Main by getting {}

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
