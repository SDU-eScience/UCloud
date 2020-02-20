//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "alerting"
    version = "1.1.22"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        if (ctx.environment in setOf(Environment.PRODUCTION, Environment.DEVELOPMENT)) {
            injectSecret("elasticsearch-logging-cluster-credentials")
        } else {
            injectSecret("elasticsearch-credentials")
        }

        injectSecret("alerting-tokens")
        deployment.spec.template.spec.serviceAccountName = this@bundle.name
    }

    withPostgresMigration(deployment)

    withLocalServiceAccount {
        addRule(
            apiGroups = listOf(""),
            resources = listOf("pods"),
            verbs = listOf("get", "watch", "list")
        )
    }
}
