dependencies {
   api(project(":accounting-service:api"))
    implementation(project(mapOf("path" to ":service-lib-server")))
    // api("com.github.java-json-tools:json-schema-validator:2.2.14")
}
