//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "elastic-management"
    version = "1.0.23"

    val deployment = withDeployment {
        injectSecret("elasticsearch-credentials")
    }

    resources.remove(deployment)

    withCronJob(deployment, "0 1 * * *", listOf("--cleanup")) {}
    withCronJob(deployment, "0 3 * * 0", listOf("--redindex")) {}
    withCronJob(deployment, "0 4 7 * *", listOf("--monthlyReduce")) {}
    withCronJob(deployment, "0 4 2 */3 *", listOf("--reduceLastQuarter")) {}

}
