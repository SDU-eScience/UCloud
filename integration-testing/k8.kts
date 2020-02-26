//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

import java.util.*

bundle { ctx ->
    name = "integration"
    version = "0.1.3"

    val userLetters = listOf("a", "b")

    val deployment =
        withDeployment(image = "registry.cloud.sdu.dk/sdu-cloud/integration-testing:${this@bundle.version}") {
            deployment.spec.replicas = 1
            userLetters.forEach { injectSecret("integration-user-$it") }
            deployment.spec.template.spec.containers.forEach { it.livenessProbe = null }
            serviceContainer.command.add("--debug")
        }

    userLetters.forEach { letter ->
        withSecret("integration-user-$letter", version = "0.1.0") {
            val scanner = Scanner(System.`in`)
            println("Please enter username for user $letter:")
            val username = scanner.nextLine()
            println("Please enter refresh token for user $letter:")
            val refreshToken = scanner.nextLine()
            secret.stringData = mapOf(
                "config.yml" to """
                    integration:
                      user${letter.toUpperCase()}:
                        username: $username
                        refreshToken: $refreshToken
                """.trimIndent()
            )
        }
    }

    fun withTest(testName: String) {
        withAdHocJob(deployment, testName, { listOf("--run-test", testName) })
    }

    withTest("support")
    withTest("avatar")
    withTest("file-favorite")
    withTest("files")
    withTest("batch-app")
    withTest("file-activity")
    withTest("concurrent-upload")
    withTest("app-search")
    withTest("concurrent-archive")
}
