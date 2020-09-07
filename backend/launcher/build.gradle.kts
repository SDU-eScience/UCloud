application {
    mainClassName = "dk.sdu.cloud.MainKt"
}

dependencies {
    implementation(project(":service-common"))

    rootProject.childProjects.values
        .filter { it.name.endsWith("-service") }
        .forEach { implementation(project(":" + it.name)) }
}
