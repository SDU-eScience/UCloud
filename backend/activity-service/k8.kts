//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "activity"
    version = "1.4.14-project34"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1

        if (ctx.environment != Environment.PRODUCTION) {
            injectSecret("elasticsearch-credentials")
        } else {
            injectSecret("elasticsearch-logging-cluster-credentials")
        }
    }

    withPostgresMigration(deployment)
}
