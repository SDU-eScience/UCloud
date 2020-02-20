//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "alerting"
    version = "1.1.22"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectSecret("elasticsearch-logging-cluster-credentials")
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
