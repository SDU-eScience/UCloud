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
    mainClassName = "dk.sdu.cloud.integration.MainKt"
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
        val jvmTest by getting {
            dependencies {
                implementation(project(":service-lib"))
                implementation(project(":service-lib-test"))

                implementation("org.testcontainers:testcontainers-bom:1.15.1")
                implementation("org.testcontainers:elasticsearch")
                implementation("it.ozimov:embedded-redis:0.7.3")
                implementation("org.testcontainers:selenium:1.15.1")
                implementation("org.seleniumhq.selenium:selenium-remote-driver:3.141.59")
                implementation("org.seleniumhq.selenium:selenium-chrome-driver:3.141.59")
                implementation("org.seleniumhq.selenium:selenium-firefox-driver:3.141.59")

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
