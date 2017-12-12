import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

group = "org.esciencecloud"
version = "1.0-SNAPSHOT"

buildscript {
    var kotlin_version: String by extra
    kotlin_version = "1.2.0"

    var ktor_version: String by extra
    ktor_version = "0.9.0"

    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(kotlinModule("gradle-plugin", kotlin_version))
    }

}

apply {
    plugin("kotlin")
}

val kotlin_version: String by extra
val ktor_version: String by extra

fun DependencyHandlerScope.ktorModule(name: String): String {
    return "io.ktor:$name:$ktor_version"
}

repositories {
    mavenLocal()
    mavenCentral()

    maven { url = URI("http://dl.bintray.com/kotlin/ktor") }
    maven { url = URI("https://dl.bintray.com/kotlin/kotlinx") }
}

dependencies {
    compile(kotlinModule("stdlib-jdk8", kotlin_version))
    //compile(group = "org.opensaml", name = "opensaml-core", version = "3.3.0")
    compile(group = "org.esciencecloud", name = "service-common", version = "0.4.0-SNAPSHOT")
    compile(group = "com.onelogin", name = "java-saml-core", version = "2.2.0")

    compile(ktorModule("ktor-server-core"))
    compile(ktorModule("ktor-server-cio"))
    compile(ktorModule("ktor-jackson"))

    compile(group = "org.apache.logging.log4j", name = "log4j-slf4j-impl", version = "2.9.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

configure<KotlinProjectExtension> {
    experimental.coroutines = Coroutines.ENABLE
}