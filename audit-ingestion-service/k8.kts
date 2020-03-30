//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "audit-ingestion"
    version = "0.1.15"

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret("elasticsearch-logging-cluster-credentials")
    }

    withPostgresMigration(deployment)
}
