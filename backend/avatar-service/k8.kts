//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle {
    name = "avatar"
    version = "2023.1.0"

    // /api/avatar is added by default
    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = Configuration.retrieve("defaultScale", "Default scale", 1)
    }

    withPostgresMigration(deployment)
}
