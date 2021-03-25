//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "mail"
    version = "0.4.2"

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: mail
                    prefix: ^/api/mail(/.*)?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: mail:8080
                    timeout_ms: 0
                    
                """.trimIndent()
            )
        )
    }

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1
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
