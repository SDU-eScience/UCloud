//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "audit-ingestion"
    version = "0.1.15"

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        if (ctx.environment in setOf(Environment.PRODUCTION, Environment.DEVELOPMENT)) {
            injectSecret("elasticsearch-logging-cluster-credentials")
        } else {
            injectSecret("elasticsearch-credentials")
        }
    }

    withPostgresMigration(deployment)
}
