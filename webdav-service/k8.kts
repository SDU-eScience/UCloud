//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "webdav"
    version = "0.1.10"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    withIngress {
        addRule("webdav.dev.cloud.sdu.dk")
        addRule("dav.cloud.sdu.dk")
    }
}
