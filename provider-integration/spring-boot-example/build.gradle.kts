import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("idea")
	id("org.springframework.boot") version "2.4.3"
	id("io.spring.dependency-management") version "1.0.11.RELEASE"
	kotlin("jvm") version "1.4.30"
	kotlin("plugin.spring") version "1.4.30"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
	mavenCentral()
    maven {
    	name = "UCloud Packages"
		url = uri("https://maven.pkg.github.com/sdu-escience/ucloud")
		credentials {
			val helpText = """
				
				
				
				
				
				Missing GitHub credentials. These are required to pull the packages required for this project. Please 
				create a personal access token here: https://github.com/settings/tokens. This access token require
				the 'read:packages' scope.
				
				With this information you will need to add the following lines to your Gradle properties
				(~/.gradle/gradle.properties):
				
				gpr.user=YOUR_GITHUB_USERNAME
				gpr.token=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
				
				
				
				
				
			""".trimIndent()
			username = (project.findProperty("gpr.user") as? String?)
				?: System.getenv("GITHUB_USERNAME") ?: error(helpText)
			password = (project.findProperty("gpr.key") as? String?)
				?: System.getenv("GITHUB_TOKEN") ?: error(helpText)
		}
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("dk.sdu.cloud:jvm-provider-support:2021.1.0")
	implementation("org.springframework:spring-web:5.3.4")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.squareup.okhttp3:okhttp:4.6.0")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

idea {
	module {
		isDownloadJavadoc = true
		isDownloadSources = true
	}
}