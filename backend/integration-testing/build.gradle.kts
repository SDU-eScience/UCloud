version = "0.2.4"

application {
    mainClassName = "dk.sdu.cloud.integration.MainKt"
}

dependencies {
    implementation(project(":service-common"))
    testImplementation(project(":service-common-test"))

    implementation(platform("org.testcontainers:testcontainers-bom:1.14.3"))
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("it.ozimov:embedded-redis:0.7.3")
    testImplementation("org.testcontainers:selenium:1.14.3")
    testImplementation("org.seleniumhq.selenium:selenium-remote-driver:3.141.59")
    testImplementation("org.seleniumhq.selenium:selenium-chrome-driver:3.141.59")
    testImplementation("org.seleniumhq.selenium:selenium-firefox-driver:3.141.59")

    rootProject.childProjects.values
        .filter { it.name.endsWith("-service") }
        .forEach {
            implementation(project(":" + it.name))
            implementation(project(":" + it.name + ":api"))
        }
}
