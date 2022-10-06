//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "audit-ingestion"
    version = "2022.2.11"

    val secret: String = config("secret", "Secret name for elasticsearch credentials", "elasticsearch-logging-cluster-credentials")

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret(secret)
    }

    withPostgresMigration(deployment)
}
