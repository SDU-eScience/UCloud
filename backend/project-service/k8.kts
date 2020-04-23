//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "project"
    version = "3.0.0-v.33"

    withAmbassador("/api/projects") {}

    val deployment = withDeployment {
        deployment.spec.replicas = when (ctx.environment) {
            Environment.PRODUCTION -> 3
            else -> 1
        }
    }

    withPostgresMigration(deployment)

    //withCronJob(deployment, "0 */12 * * *", listOf("--remind")) {}
    withAdHocJob(deployment, "remind-now", { listOf("--remind") })
}
