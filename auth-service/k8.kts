//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle {
    name = "auth"
    version = "1.27.3"

    withAmbassador("/auth") {
        addSimpleMapping("/api/sla")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
        injectSecret("auth-certs")
        injectSecret("auth-wayf", "/etc/wayf-certs")
        injectSecret("auth-wayf-config")
        injectConfiguration("auth-config")
    }

    withPostgresMigration(deployment)
    withCronJob(deployment, "0 2 * * 1", listOf("--tokenScan")) {}

    fun host(environment: Environment): String {
        return when (environment) {
            Environment.DEVELOPMENT -> "dev.cloud.sdu.dk"
            Environment.PRODUCTION -> "cloud.sdu.dk"
            Environment.TEST -> "staging.dev.cloud.sdu.dk"
        }
    }

    fun scheme(environment: Environment): String = "https"

    withConfigMap("token-validation") { ctx ->
        val cert: String = when (ctx.environment) {
            Environment.PRODUCTION -> {
                """
                    -----BEGIN CERTIFICATE-----
                    MIIDNDCCAhwCCQCshN+wCG6lEzANBgkqhkiG9w0BAQsFADBcMQswCQYDVQQGEwJE
                    SzEdMBsGA1UECgwUU3lkZGFuc2sgVW5pdmVyc2l0ZXQxFzAVBgNVBAsMDmVTY2ll
                    bmNlQ2VudGVyMRUwEwYDVQQDDAxjbG91ZC5zZHUuZGswHhcNMTgxMTE1MTMwNjU2
                    WhcNMTkxMTE1MTMwNjU2WjBcMQswCQYDVQQGEwJESzEdMBsGA1UECgwUU3lkZGFu
                    c2sgVW5pdmVyc2l0ZXQxFzAVBgNVBAsMDmVTY2llbmNlQ2VudGVyMRUwEwYDVQQD
                    DAxjbG91ZC5zZHUuZGswggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCf
                    ZMpChrOcUx4sPr3FXrpJ6n77H0ueHXzMIGKyh7+JZuScB77eMwWa6MCeo58byzPu
                    1u6Je6W3QWt/vWYNFwj+yAFv9FRjh67mpB7v4Tew6P1HcIrSBE6P+cOdtDO1augf
                    fAI8K77FuVC7ZVlTWwP2wjOQIvBTOEtoTN1IOAlmbFwRkX+rRwZ1U53ZNo17PW0T
                    QHxdD90NbYqx/wuFus1UdgBrI0uVTOmJG7ohiWt8bpW5mz+et4SGgFGl2LD6mv4l
                    etHzhOJgMMVEAA8o5TwwxCYw5QaGdLtZ1jPTfWj3w0wJxPTcPj39unI/ztfrW+OG
                    efHsK02igOfRKv8rbKcJAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAG4KYesD3ZM/
                    fh5lHFd6BcCCC6n9+TkeeFqNoWnmeQN6HZAv7X4OdQ3wyMV2fbeGWRG23OjqcTud
                    5Z1NfXZPoVCq+PeG1PcUsgA5iTSNpPENGEJHp1fVFJIrRjaaVUYa5mB58M8x29Hi
                    52DnIKUth4svRL5JtikuNEtFWOUmoX4QNrgxPGyRaqGwWNXD5EUMRgVeaq97rBB1
                    euWW6VhEvo5S5p64K0E1EjGHv3N384/Nu8+P7sKX3vQorNiidnSJlMl+VARcV6k9
                    eWK+YvfER32gylkRqG56k2oC9AuRKV88mLVCV7HcpA2Q1gDIqRVXhMavgFZ+Mxh+
                    Ms12PEWBG3Q=
                    -----END CERTIFICATE-----
                """.trimIndent()
            }

            Environment.DEVELOPMENT -> {
                """
                    -----BEGIN CERTIFICATE-----
                    MIIDZDCCAkwCCQD7FaLkoHJ8yzANBgkqhkiG9w0BAQsFADB0MQswCQYDVQQGEwJE
                    SzETMBEGA1UECAwKU3lkZGFubWFyazEPMA0GA1UEBwwGT2RlbnNlMREwDwYDVQQK
                    DAhTRFVDbG91ZDERMA8GA1UECwwIU0RVQ2xvdWQxGTAXBgNVBAMMEGRldi5jbG91
                    ZC5zZHUuZGswHhcNMTkxMjA5MTIxNDQ3WhcNMjAxMjA4MTIxNDQ3WjB0MQswCQYD
                    VQQGEwJESzETMBEGA1UECAwKU3lkZGFubWFyazEPMA0GA1UEBwwGT2RlbnNlMREw
                    DwYDVQQKDAhTRFVDbG91ZDERMA8GA1UECwwIU0RVQ2xvdWQxGTAXBgNVBAMMEGRl
                    di5jbG91ZC5zZHUuZGswggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCf
                    WREVDhCHCiz5qsdXeNJBF4Kq4LRTwAlR70mFRHdhLQ1pOv8zMDF/9Ai3L1Xze2qZ
                    e148Bea4msQBAND3OIvWw+4tyIvk0ZsE7Tc6vDfABbaLA51WjZ2D+ENE+QTTkLoa
                    t3fv+d4F4K+hhqfO4mggUBMpTm88OKAF0oXkG+fVsQkGkc47B7J6JmIdtX6mqwCD
                    Y3tlIouuc+xldK7ZLvwZhlyW5Esg0EpkT1dStniBvO/8/Gklp3VYu7V/bGFkIu3O
                    G6FTicaNbF6re/DbmQxqcHnAiiPKm6z+b9LwsMJ8SfgG/fjuit8Vr5IbBHCV8YDl
                    zUS5SHcL1Cy1LOaQ7Qe9AgMBAAEwDQYJKoZIhvcNAQELBQADggEBAENha7xCQfiZ
                    wzHrw58nahechm4qNrypV8H6uT8tZ/C2ZZyBN01QtzfJ3xCwuC3qHTP4yc0hfPP6
                    kLAm5K7sn7jFOf3i2E3AObCNmsV97yQeiHTlvoW+7U9ucOO/7RuQ89r0nWzcjA/k
                    NMUAnL91O2yw8SvX1IRuxMvsDzOSdDUzynOZJL/gvbDciYWzeFz8LuDLsJqqNTmm
                    dvWcew0MVVCYkAMeAYgWH2is2e4geuhC+WHlWoJR8eGzUS2aBgWzGefG27fMhJly
                    lbLNiSExivmstH1xCrjPjhhhxLnmhDvEiWL6QfnBKaV5qDWf4LWKnlg4BieFM4IO
                    w5Yy70atUaI=
                    -----END CERTIFICATE-----
                """.trimIndent()
            }

            Environment.TEST -> TODO()
        }

        addConfig(
            "rpc.yml",
            //language=yml
            """
                rpc:
                  client:
                    host:
                      host: ${host(ctx.environment)}
                      scheme: ${scheme(ctx.environment)}
            """.trimIndent()
        )

        addConfig(
            "tokenvalidation.yml",
            mapOf<String, Any?>(
                "tokenValidation" to mapOf<String, Any?>(
                    "jwt" to mapOf<String, Any?>(
                        "publicCertificate" to cert
                    )
                )
            )
        )
    }

    withConfigMap("auth-config") { ctx ->
        val trustedOrigins: List<String> = when (ctx.environment) {
            Environment.TEST, Environment.DEVELOPMENT -> listOf("localhost", host(ctx.environment))
            Environment.PRODUCTION -> listOf(host(ctx.environment))
        }

        val enableWayf = ctx.environment == Environment.PRODUCTION

        data class AuthService(
            val name: String,
            val endpoint: String,
            val serviceMode: String = "WEB",
            val endpointAcceptStateViaCookie: Boolean = false,
            val refreshTokenExpiresAfter: Long = 2592000000
        )

        val base = "${scheme(ctx.environment)}://${host(ctx.environment)}"
        val services: List<AuthService> =
            listOf(
                AuthService("web-csrf", "$base/api/auth-callback-csrf"),
                AuthService("web", "$base/app/login/wayf", endpointAcceptStateViaCookie = true),
                AuthService("dav", "$base/app/login/wayf?dav=true", serviceMode = "APPLICATION")
            )

        data class TokenExtension(val serviceName: String, val allowedScopes: List<String>)

        val tokenExtension = listOf(
            TokenExtension(
                "_app", listOf(
                    "files.upload:write",
                    "files.download:write",
                    "files.createDirectory:write",
                    "files.stat:read",
                    "files.extract:write"
                )
            ),
            TokenExtension(
                "_activity", listOf(
                    "files:read"
                )
            ),
            TokenExtension(
                "_share", listOf(
                    "files.updateAcl:write",
                    "files.createLink:write",
                    "files.deleteFile:write",
                    "files.stat:read",
                    "files.jobs.queryBackgroundJob:read"
                )
            ),
            TokenExtension(
                "_app-orchestrator", listOf(
                    "files.upload:write",
                    "files.download:write",
                    "files.createDirectory:write",
                    "files.stat:read",
                    "files.extract:write",
                    "app.fs:read"
                )
            )
        )

        addConfig(
            "config.yml",
            mapOf(
                "auth" to mapOf(
                    "wayfCerts" to if (enableWayf) "/etc/wayf-certs" else "/etc/auth-certs",
                    "trustedOrigins" to trustedOrigins,
                    "certsLocation" to "/etc/auth-certs",
                    "enablePasswords" to true,
                    "enableWayf" to enableWayf,
                    "production" to true, // As in not local development
                    "services" to services,
                    "tokenExtension" to tokenExtension
                )
            )
        )
    }
}
