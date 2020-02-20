//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

import java.io.File
import java.util.*

bundle { ctx ->
    name = "auth"
    version = "1.27.4"

    fun host(environment: Environment): String {
        return when (environment) {
            Environment.DEVELOPMENT -> "dev.cloud.sdu.dk"
            Environment.PRODUCTION -> "cloud.sdu.dk"
            Environment.TEST -> "staging.dev.cloud.sdu.dk"
        }
    }

    fun scheme(environment: Environment): String = "https"

    withAmbassador("/auth") {
        addSimpleMapping("/api/sla")
    }

    val deployment = withDeployment(injectServiceSecrets = false) {
        deployment.spec.replicas = 2
        injectSecret("auth-certs")
        injectSecret("auth-wayf", "/etc/wayf-certs")
        injectSecret("auth-wayf-config")
        injectConfiguration("auth-config")
    }

    withPostgresMigration(deployment) {
        val volumeBlacklist = setOf(
            "auth-wayf",
            "auth-wayf-config"
        )
        job.spec.template.spec.volumes =
            job.spec.template.spec.volumes.filter { it.secret?.secretName !in volumeBlacklist } +
                    volumeBlacklist.map { vol ->
                        Volume().apply {
                            name = vol
                            emptyDir = EmptyDirVolumeSource()
                        }
                    }
    }

    withCronJob(deployment, "0 2 * * 1", listOf("--tokenScan")) {}

    withSecret("auth-wayf", version = "0") {
        when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> {
                // Do nothing
            }

            Environment.PRODUCTION -> {
                TODO()
            }
        }
    }

    withSecret("auth-wayf-config", version = "0") {
        when (ctx.environment) {
            Environment.DEVELOPMENT, Environment.TEST -> {
                // Do nothing
            }

            Environment.PRODUCTION -> {
                TODO()
            }
        }
    }

    withSecret("auth-refresh-token") {
        val client = ctx.client
        val proxyPod = client.pods().inNamespace("stolon").list().items.find { it.metadata.name.contains("proxy") }
            ?: throw IllegalStateException("Could not find stolon proxy")

            client.secrets()
                .inNamespace(ctx.namespace)
                .withName("auth-psql")
                .get()
                ?: throw IllegalStateException("auth-psql must be configured first")

        val stolonPassword =
            client.secrets()
                .inNamespace("stolon")
                .withName("stolon")
                .get()
                ?.data
                ?.get("pg_su_password")
                ?.let { Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }

        fun executeStatement(statement: String) {
            val exec =
                client.pods().inNamespace("stolon").withName(proxyPod.metadata.name).execWithDefaultListener(
                    listOf(
                        "psql",
                        "-c",
                        statement,
                        "postgresql://stolon:${stolonPassword}@localhost/postgres"
                    ),
                    attachStderr = true,
                    attachStdout = true
                )

            println(exec.stdout?.bufferedReader()?.readText())
            println(exec.stderr?.bufferedReader()?.readText())
        }

        val refreshToken = UUID.randomUUID().toString()
        executeStatement("insert into auth.principals(dtype, id, created_at, modified_at, role) values " +
                "('ServiceEntity', '_auth', now(), now(), 'SERVICE');")

        executeStatement("insert into auth.refresh_tokens(token, associated_user_id, csrf, public_session_reference, " +
                "extended_by, scopes, expires_after, refresh_token_expiry, extended_by_chain, created_at, ip, " +
                "user_agent) " +
                "values ('$refreshToken', '_auth', 'blank', 'auth', null, '[\"all:write\"]', 600000, null, '[]', " +
                "now(), null, null)")

        secret.stringData = mapOf(
            "config.yml" to "refreshToken: $refreshToken"
        )
    }

    withSecret("auth-psql", version = "1") {
        val client = ctx.client
        val proxyPod = client.pods().inNamespace("stolon").list().items.find { it.metadata.name.contains("proxy") }
            ?: throw IllegalStateException("Could not find stolon proxy")

        val stolonPassword =
            client.secrets()
                .inNamespace("stolon")
                .withName("stolon")
                .get()
                ?.data
                ?.get("pg_su_password")
                ?.let { Base64.getDecoder().decode(it).toString(Charsets.UTF_8) }

        fun executeStatement(statement: String) {
            val exec =
                client.pods().inNamespace("stolon").withName(proxyPod.metadata.name).execWithDefaultListener(
                    listOf(
                        "psql",
                        "-c",
                        statement,
                        "postgresql://stolon:${stolonPassword}@localhost/postgres"
                    ),
                    attachStderr = true,
                    attachStdout = true
                )

            println(exec.stdout?.bufferedReader()?.readText())
            println(exec.stderr?.bufferedReader()?.readText())
        }

        val dbUser = name.replace('-', '_')
        val schema = dbUser
        val generatedPassword = UUID.randomUUID().toString()

        executeStatement("drop owned by \"$dbUser\" cascade;")
        executeStatement("drop schema \"$schema\";")
        executeStatement("drop user \"$schema\";")
        executeStatement("create user \"$dbUser\" password '$generatedPassword';")
        executeStatement("create schema \"$schema\" authorization \"$dbUser\";")

        secret.stringData = mapOf(
            "db.yml" to """
                    ---
                    hibernate:
                        database:
                            profile: PERSISTENT_POSTGRES
                            credentials:
                                username: $dbUser
                                password: $generatedPassword
                            
                """.trimIndent()
        )

        // Hack: Migration script needs an empty refresh-token. We will now inject one:
        if (client.secrets().inNamespace(ctx.namespace).withName("auth-refresh-token").get() == null) {
            client.secrets().inNamespace(ctx.namespace).create(Secret().apply {
                metadata = ObjectMeta()
                metadata.name = "auth-refresh-token"

                stringData = mapOf(
                    "config.yml" to "refreshToken: empty-refresh-token"
                )
            })
        }
    }

    withSecret("auth-certs", version = "1") {
        val scanner = Scanner(System.`in`)
        val certsRoot = File(ctx.repositoryRoot, "auth-service/certs")
        certsRoot.mkdirs()
        println("Looking for certificates in ${certsRoot.absolutePath}")
        val certPem = File(certsRoot, "cert.pem")
        val keyPem = File(certsRoot, "key.pem")
        if (!certPem.exists() || !keyPem.exists()) {
            println("Could not find certificates!")
            print("Do you want to generate these? [y/n] ")
            while (true) {
                if (scanner.nextLine().equals("y", ignoreCase = true)) {
                    ProcessBuilder()
                        .command(
                            "bash",
                            File(ctx.repositoryRoot, "auth-service/generate_self_signed_certs").absolutePath
                        )
                        .directory(File(ctx.repositoryRoot, "auth-service"))
                        .start()
                        .waitFor()
                    break
                } else {
                    throw IllegalStateException("No certificates")
                }
            }
        }

        if (!certPem.exists() || !keyPem.exists()) {
            throw IllegalStateException("No certs after generation")
        }

        print("Do you wish to use these certificates? [y/n] ")
        while (true) {
            if (scanner.nextLine().equals("y", ignoreCase = true)) {
                repeat(10) {
                    println("!!! Please make sure cert.pem contents is added to token-validation config map !!!")
                    println("!!! See auth-service/k8.kts                                                    !!!")
                }

                secret.stringData = mapOf(
                    "cert.pem" to certPem.readText(),
                    "key.pem" to keyPem.readText()
                )
                break
            } else {
                throw IllegalStateException("Terminating")
            }
        }
    }

    withConfigMap("token-validation") {
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

            Environment.TEST -> {
                """
                    -----BEGIN CERTIFICATE-----
                    MIIDSDCCAjACCQCOMA9vihmVcDANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJE
                    SzETMBEGA1UECAwKU3lkZGFubWFyazELMAkGA1UEBwwCTkExETAPBgNVBAoMCGVT
                    Y2llbmNlMREwDwYDVQQLDAhlU2NpZW5jZTEPMA0GA1UEAwwGdWNsb3VkMB4XDTIw
                    MDIxODEzNDYyNloXDTIxMDIxNzEzNDYyNlowZjELMAkGA1UEBhMCREsxEzARBgNV
                    BAgMClN5ZGRhbm1hcmsxCzAJBgNVBAcMAk5BMREwDwYDVQQKDAhlU2NpZW5jZTER
                    MA8GA1UECwwIZVNjaWVuY2UxDzANBgNVBAMMBnVjbG91ZDCCASIwDQYJKoZIhvcN
                    AQEBBQADggEPADCCAQoCggEBAK8TIpxSyi1BwiiAFTDTHFVXSa/nHT882s22hikk
                    6mljQohCqHXGpSTuINMM5ma5wdsqFfYb/nIz7fXdDktW/hAbhDIkoOLGxb4BJx+S
                    /Ce3LZXSKlT8CxJ+Ayw66APG2ntksqQVkKvPD+HUpSEV5mXR+E+3uzj8Vd8e1SYi
                    h/423zfJ8bJA7TSripi85BWzwMbWJYbLT4wW1PwOhNpwhqqClTjcnlfeqBb3SMmj
                    pgKg5bM6YuZKyoSrKMF2WjzBxw1aOwBRKbO8Z12I8noFeGDw9+w/caYG+ZvusIEy
                    oTk/+zhG8hRRyNa2RCAZspz06jaCUV1aFxX7Hl/yvRQAu6sCAwEAATANBgkqhkiG
                    9w0BAQsFAAOCAQEAAmgal9lScwRkLMV2CSBhCcnog/PL5bIiQj0HieRkHb8gUiwk
                    OGbOxDH2P/Gn/4WDO6SwQDXb8L/Kk3e+jDD8snt2n1Pqmw/7WgemTZoEQMVKuWGM
                    TseyfMEA6PFA7OZTi4CMtfq/Qh0rlPN72wA3fxjOS8upku9X0S2gsLuNQorj8R/5
                    iAHo/fPNAmAHVI8cyQRWbcP5K7TvEf3Ij9yBByas50AHoMdjCoSFqoIVtiT8JlMa
                    0iRq2Uj2uVqPocy2tJ2K27FHKRR3H6YSdjzZ/Ys+9kxc22I40nLVLfCdnBV9/GGU
                    a/e5GT6FiQPV79ntyelUEQCkqT7Xr0uSt4Cc2g==
                    -----END CERTIFICATE-----
                """.trimIndent()
            }
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

    withConfigMap("auth-config") {
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
                AuthService("dev-web", "http://localhost:9000/app/login/wayf", endpointAcceptStateViaCookie = true),
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
