version = "0.2.4"

application {
    mainClassName = "dk.sdu.cloud.integration.MainKt"
}

dependencies {
    implementation(project(":service-common"))
    testImplementation(project(":service-common-test"))

    rootProject.childProjects.values
        .filter { it.name.endsWith("-service") }
        .forEach {
            implementation(project(":" + it.name))
            implementation(project(":" + it.name + ":api"))
        }
}
