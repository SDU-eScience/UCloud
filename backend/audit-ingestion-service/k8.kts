//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "audit-ingestion"
    version = "0.2.0"

    val secret: String = config("secret", "Secret name for elasticsearch credentials", "elasticsearch-credentials")

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret(secret)
    }

    withPostgresMigration(deployment)
}
