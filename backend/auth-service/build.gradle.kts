version = rootProject.file("./version.txt").readText().trim()

application {
    mainClass.set("dk.sdu.cloud.auth.MainKt")
}

kotlin.sourceSets {
    val main by getting {
        dependencies {
            // SAML
            implementation("com.onelogin:java-saml-core:2.5.0")

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
    }
}
