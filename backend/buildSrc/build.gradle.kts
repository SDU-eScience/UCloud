plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("record") {
            id = "record"
            implementationClass = "RecordPlugin"
        }
    }
}
