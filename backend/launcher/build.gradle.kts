application {
    mainClassName = "dk.sdu.cloud.MainKt"
}

dependencies {
    implementation(project(":service-common"))
    implementation("io.swagger.core.v3:swagger-models:2.1.5")
    implementation("io.swagger.core.v3:swagger-core:2.1.5")

    rootProject.childProjects.values
        .filter { it.name.endsWith("-service") }
        .forEach {
            implementation(project(":" + it.name))
            implementation(project(":" + it.name + ":api"))
        }
}
