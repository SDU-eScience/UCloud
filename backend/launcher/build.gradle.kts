plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    application
}

repositories {
    jcenter()
    mavenCentral()
}

application {
    mainClassName = "dk.sdu.cloud.MainKt"
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
                implementation("io.swagger.core.v3:swagger-models:2.1.5")
                implementation("io.swagger.core.v3:swagger-core:2.1.5")
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.4.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.0")

                rootProject.childProjects.values
                    .filter { it.name.endsWith("-service") }
                    .forEach {
                        implementation(project(":" + it.name))
                        implementation(project(":" + it.name + ":api"))
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
