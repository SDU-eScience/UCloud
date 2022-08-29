kotlin.sourceSets {
    val main by getting {
        dependencies {
            implementation(project(":auth-service:api"))
            implementation(project(":accounting-service:api"))
            implementation(project(":accounting-service:util"))
        }
    }
}
