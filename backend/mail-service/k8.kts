//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "mail"
    version = "2022.1.0"

    withAmbassador("/api/mail") {}

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = Configuration.retrieve("defaultScale", "Default scale", 1)
        injectConfiguration("mail-config")
        injectSecret("alerting-tokens")
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
                  - "_grant"
                  - "_accounting"
                  fromAddress: "support@escience.sdu.dk"
            """.trimIndent()
        )
    }

    withPostgresMigration(deployment)
    withAdHocJob(deployment, "test", { listOf("--send-test-mail") })
}