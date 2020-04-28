//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "elastic-management"
    version = "1.0.26"

    val deployment = withDeployment {
        injectSecret("elasticsearch-logging-cluster-credentials")
        injectSecret("elasticmanagement-tokens")
    }

    withCronJob(deployment, "0 1 * * *", listOf("--cleanup"), name="elastic-cleanup") {}
    withCronJob(deployment, "0 3 * * 0", listOf("--reindex"), name="elastic-weekly-reindex") {}
    withCronJob(deployment, "0 4 7 * *", listOf("--monthlyReduce"), name="elastic-montly-reduce") {}
    withCronJob(deployment, "0 4 2 */3 *", listOf("--reduceLastQuarter"), name="elastic-reduce-quarter") {}

    resources.remove(deployment)
}
