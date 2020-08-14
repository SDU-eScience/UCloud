//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "app-license"
    version = "0.2.0"

    withAmbassador("/api/app/license") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }

    withPostgresMigration(deployment)
}
