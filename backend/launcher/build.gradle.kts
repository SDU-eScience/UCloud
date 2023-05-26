import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
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
    jvmToolchain(17)

    sourceSets {
        val main by getting {
            dependencies {
                implementation(project(":service-lib"))
                implementation(project(":service-lib-server"))
                implementation(kotlin("compiler-embeddable"))
                implementation(kotlin("reflect"))

                rootProject.childProjects.values
                    .filter { it.name.endsWith("-service") }
                    .forEach { p ->
                        implementation(project(":" + p.name))

                        val hasApiProject = rootProject.subprojects
                            .find { it.name == p.name }!!.subprojects
                            .any { it.name == "api" }
                        if (hasApiProject) implementation(project(":" + p.name + ":api"))

                        val hasUtilProject = rootProject.subprojects
                            .find { it.name == p.name }!!.subprojects
                            .any { it.name == "util" }
                        if (hasUtilProject) implementation(project(":" + p.name + ":util"))
                    }
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

tasks.withType<org.gradle.api.tasks.JavaExec>().configureEach {
    systemProperty("log4j2.configurationFactory", "dk.sdu.cloud.micro.Log4j2ConfigFactory")
}

version = rootProject.file("./version.txt").readText().trim()

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
