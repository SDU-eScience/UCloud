//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "file-trash"
    version = "1.5.0"

    withAmbassador("/api/files/trash") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }
}
