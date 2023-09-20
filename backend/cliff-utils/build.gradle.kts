plugins {
    id("java")
    id("maven-publish")
}

version = rootProject.file("./version.txt").readText().trim()
extensions.configure<PublishingExtension>("publishing") {
    repositories {
        maven {
            mavenLocal()

            maven {
                name = "UCloudMaven"
                url = uri("https://mvn.cloud.sdu.dk/releases")
                credentials {
                    username = (project.findProperty("ucloud.mvn.username") as? String?)
                        ?: System.getenv("UCLOUD_MVN_USERNAME")
                    password = (project.findProperty("ucloud.mvn.token") as? String?)
                        ?: System.getenv("UCLOUD_MVN_TOKEN")
                }
            }
        }
    }

    publications {
        create<MavenPublication>("api") {
            from(components["java"])
        }

        all {
            if (this is MavenPublication) {
                this.groupId = "org.cliffc.high_scale_lib"
                this.artifactId = "cliff-utils"
            }
        }
    }
}
