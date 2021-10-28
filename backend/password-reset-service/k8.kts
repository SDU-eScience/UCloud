//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "password-reset"
    version = "2021.3.0-alpha0"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }

    withPostgresMigration(deployment)
}
