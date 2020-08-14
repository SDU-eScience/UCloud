//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "activity"
    version = "1.5.0"

    // Fetch configuration from audit-ingestion
    val elasticCredentials = Configuration.retrieve<String>(
        "audit-ingestion.secret",
        "Secret name for elasticsearch credentials",
        "elasticsearch-credentials"
    )

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1

        injectSecret(elasticCredentials)
    }

    withPostgresMigration(deployment)
}
