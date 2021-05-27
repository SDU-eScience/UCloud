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
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
            languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
            languageSettings.useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

version = "2021.2.0-storage0"
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
