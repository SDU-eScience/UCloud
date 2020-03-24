//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "app-orchestrator"
    version = "2.0.0"

    withAmbassador(null) {
        addSimpleMapping("/api/hpc/jobs")
        addSimpleMapping("/api/app/compute")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectConfiguration("app-config")
    }

    withPostgresMigration(deployment)

    withConfigMap(name = "app-config") {
        data class MachineType(val name: String, val cpu: Int?, val memoryInGigs: Int?, val gpu: Int? = null)
        data class ComputeBackend(val name: String, val title: String, val useWorkspaces: Boolean)

        val machines: List<MachineType> = when (ctx.environment) {
            Environment.TEST, Environment.DEVELOPMENT -> {
                listOf(
                    MachineType("Unspecified", null, null),
                    MachineType("Small (S)", 1, 4),
                    MachineType("Medium (M)", 4, 16),
                    MachineType("Large (L)", 16, 32)
                )
            }

            Environment.PRODUCTION -> {
                listOf(
                    MachineType("Unspecified", null, null),
                    MachineType("Small (S)", 1, 4),
                    MachineType("Medium (M)", 4, 16),
                    MachineType("Large (L)", 16, 32),
                    MachineType("Extra Large (XL)", 32, 160),
                    MachineType("Extra Extra Large (XXL)", 62, 350),
                    MachineType("GPU (S)", 20, 42, 1),
                    MachineType("GPU (M)", 40, 84, 2),
                    MachineType("GPU (L)", 78, 170, 4)
                )
            }
        }

        val config: Map<String, Any?> = mapOf(
            "app" to mapOf(
                "defaultBackend" to "kubernetes",
                "backends" to listOf(
                    ComputeBackend("kubernetes", "k8s", true)
                ),
                "machines" to machines
            )
        )

        addConfig("config.yml", config)
    }
}
