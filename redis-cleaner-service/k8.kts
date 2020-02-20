//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "redis-cleaner"
    version = "0.1.10"

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }.also {
        // We don't actually want the deployment but we do want the template
        resources.remove(it)
    }

    withCronJob(deployment, "0 3 * * 0", emptyList()) {}
    withAdHocJob(deployment, "-now", { emptyList() })
}
