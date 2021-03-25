//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle {
    name = "elastic-management"
    version = "1.3.1"

    val deployment = withDeployment {
        injectSecret("elasticsearch-logging-cluster-credentials")
        injectSecret("elasticmanagement-tokens")
    }

    withAdHocJob(deployment, "elastic-insta-clean", { listOf("--cleanup")}) {}

    withCronJob(deployment, "*/10 * * * *", listOf("--entryDelete"), name="elastic-entry-cleaner") {}
    withCronJob(deployment, "0 1 * * *", listOf("--cleanup"), name="elastic-cleanup") {}
    withCronJob(deployment, "0 3 * * *", listOf("--reindex"), name="elastic-reindex") {}
    withCronJob(deployment, "0 */3 * * *", listOf("--grafanaAliases"), name="manage-grafana-alias") {}


    resources.remove(deployment)
}
