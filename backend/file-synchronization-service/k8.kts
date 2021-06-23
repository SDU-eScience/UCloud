//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "file-synchronization"
    version = "0.1.0"

    withAmbassador("/api/files/synchronization") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }
}
