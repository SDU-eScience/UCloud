//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "accounting"
    version = "1.7.10"

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                  ---
                  apiVersion: ambassador/v1
                  kind: Mapping
                  name: accounting-1
                  prefix: ^/api/accounting(/.*)?$
                  prefix_regex: true
                  service: accounting:8080
                  rewrite: ""
                  timeout_ms: 0
                """.trimIndent()
            )
        )

        services.add(
            AmbassadorMapping(
                """
                  ---
                  apiVersion: ambassador/v1
                  kind: Mapping
                  name: accounting-2
                  prefix: ^/api/products(/.*)?$
                  prefix_regex: true
                  service: accounting:8080
                  rewrite: ""
                  timeout_ms: 0
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 4
    }

    withPostgresMigration(deployment)

    withAdHocJob(deployment, "instant-check-wallets", { listOf("--low-funds-check")}) {}
    withCronJob(deployment, "0 */12 * * *", listOf("--low-funds-check"), name="check-wallets") {}
}