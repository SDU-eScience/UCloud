//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "accounting-storage"
    version = "1.4.0-projects.1"

    withAmbassador("/api/accounting/storage") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)
}
