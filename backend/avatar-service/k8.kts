//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle {
    name = "avatar"
    version = "1.3.8"

    // /api/avatar is added by default
    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)
}
