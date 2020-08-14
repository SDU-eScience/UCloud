version = "1.28.0"

application {
    mainClassName = "dk.sdu.cloud.auth.MainKt"
}

dependencies {
    // SAML
    implementation(group = "com.onelogin", name = "java-saml-core", version = "2.5.0")

    // 2FA
    implementation("com.warrenstrange:googleauth:1.1.2") {
        // Jesus Christ... Why on Earth do you need to depend on this!?
        //
        // We don"t need it. Thankfully code can work without it (as long as we stay away from components
        // that depend on it)
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }

    // QR-codes
    implementation("com.google.zxing:core:3.3.0")
    implementation("com.google.zxing:javase:3.3.0")
}
