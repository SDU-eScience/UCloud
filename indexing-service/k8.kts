//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "indexing"
    version = "1.15.9"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2

        injectSecret("elasticsearch-credentials")
    }

    withPostgresMigration(deployment)
    withCronJob(deployment, "0 */12 * * *", listOf("--scan")) {}
    withAdHocJob(deployment, "-scan-now", { listOf("--scan") })
}
