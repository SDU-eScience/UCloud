//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "project"
    version = "3.2.2"

    val enabled: Boolean = config("enabled", "Should projects be enabled", false)

    withAmbassador("/api/projects") {}

    val deployment = withDeployment {
        injectConfiguration("project-config")
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    //withCronJob(deployment, "0 */12 * * *", listOf("--remind")) {}
    withAdHocJob(deployment, "remind-now", { listOf("--remind") })

    withConfigMap("project-config", version = "1") {
        addConfig(
            "config.yml",
            mapOf<String, Any?>(
                "project" to mapOf<String, Any?>(
                    "enabled" to enabled
                )
            )
        )
    }
}
