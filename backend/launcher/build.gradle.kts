plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    application
    id("maven-publish")
}

repositories {
    jcenter()
    mavenCentral()
}

application {
    mainClass.set("dk.sdu.cloud.MainKt")
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
                implementation(project(":service-lib"))
                implementation(project(":service-lib-server"))
                implementation("io.swagger.core.v3:swagger-models:2.1.5")
                implementation("io.swagger.core.v3:swagger-core:2.1.5")
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.4.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")

                rootProject.childProjects.values
                    .filter { it.name.endsWith("-service") }
                    .forEach { p ->
                        implementation(project(":" + p.name))

                        val hasApiProject = rootProject.subprojects
                            .find { it.name == p.name }!!.subprojects
                            .any { it.name == "api" }
                        if (hasApiProject) implementation(project(":" + p.name + ":api"))
                    }
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

version = "1.0.0"

publishing {
    repositories {
        maven {
            mavenLocal()
        }
    }
}

tasks.withType<Jar> {
    val name = "ucloud-launcher"
    archiveName = "$name.jar"
}
