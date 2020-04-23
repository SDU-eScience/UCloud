//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "mail"
    version = "0.1.0"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectConfiguration("mail-config")
    }

    withConfigMap("mail-config") {
        addConfig(
            "config.yml",
            //language=yaml
            """
                mail:
                  whitelist:
                  - "_password-reset"
                  - "_project"
                  fromAddress: "support@escience.sdu.dk"
            """.trimIndent()
        )
    }

    withPostgresMigration(deployment)
    withAdHocJob(deployment, "test", { listOf("--send-test-mail") })
}
