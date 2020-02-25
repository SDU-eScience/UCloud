//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "indexing"
    version = "1.15.11"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2

        injectSecret("elasticsearch-credentials")
    }

    withPostgresMigration(deployment)
    withCronJob(deployment, "0 */12 * * *", listOf("--scan")) {}
    withAdHocJob(deployment, "-scan-now", { listOf("--scan") })

    val numberOfShards = when (ctx.environment) {
        Environment.DEVELOPMENT -> 5
        Environment.PRODUCTION -> 5
        Environment.TEST -> 2
    }

    val numberOfReplicas = when (ctx.environment) {
        Environment.PRODUCTION -> 2
        Environment.DEVELOPMENT -> 2
        Environment.TEST -> 1
    }

    withMigration(
        deployment,
        listOf("--create-index", "$numberOfShards", "$numberOfReplicas"),
        "create-index",
        version = "1.15.10"
    )
}
