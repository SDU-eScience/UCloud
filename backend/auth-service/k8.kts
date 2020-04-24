//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

import java.io.File
import java.util.*

bundle { ctx ->
    name = "auth"
    version = "1.27.4-email-userinfo1"

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

    withSecret("auth-refresh-token", version = "1") {
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

    withConfigMap("token-validation", version = "1") {
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
                    MIIDfzCCAmegAwIBAgIUDlxDPskNFRztsjog68XuA4jjGZkwDQYJKoZIhvcNAQEL
                    BQAwTzELMAkGA1UEBhMCREsxDDAKBgNVBAgMA0Z5bjEPMA0GA1UEBwwGT2RlbnNl
                    MREwDwYDVQQKDAhlU2NpZW5jZTEOMAwGA1UEAwwFQnJpYW4wHhcNMTkxMDAxMTEz
                    NTM0WhcNMjAwOTMwMTEzNTM0WjBPMQswCQYDVQQGEwJESzEMMAoGA1UECAwDRnlu
                    MQ8wDQYDVQQHDAZPZGVuc2UxETAPBgNVBAoMCGVTY2llbmNlMQ4wDAYDVQQDDAVC
                    cmlhbjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMBg0lJG0fpH02pc
                    46DEfBm4xe0caQpptxQX4uq8Bl4jW8Lf+ycl2UC3qP/ccCsBmAvmSS1DWRnxSDjc
                    eWvSP7oYJarVhPlavj/uNpRDCIs0JBGIRvJB2szriJCwxJXtAyAqTOi9apUQq8we
                    9gXB2E48HLo230xnOfUR1++O01aQOVosIrNZvZEwxP6HHXL6TYVRRzlfi0OgYjMs
                    by5Jx65l2HVqJZGV/WOfwBYVdaJEJGM3PMXIuZSJmRJX/clgrjCeaQRFMt/BDPnF
                    sjfg2xuTZz8dhDpsYel2d9GdDpmI5Yb7bfXaj2AYZ+KXcGIuhNPV8dycvSFgqH4B
                    btTrFwUCAwEAAaNTMFEwHQYDVR0OBBYEFJpREMDgQ+CYNKWKE955VW5GtE82MB8G
                    A1UdIwQYMBaAFJpREMDgQ+CYNKWKE955VW5GtE82MA8GA1UdEwEB/wQFMAMBAf8w
                    DQYJKoZIhvcNAQELBQADggEBAKKhwgVtqPxoAmaKjC/i4KWpYltCBZtQB0NwXRxp
                    WlFZ/rnPxA8dCDej1T/dvW3LgCF2su91e44ImH/z+6liJa6O5yHxs/rWT5RsdDNy
                    gFMmOBcCHgCS1bcHyz0ZUtOkPvFLODC2vfdxKa3fks7C5O2CKDsBkIMxqu/TMU1S
                    /DY5UHyr0nI2jur2M/xcYTEg4RuQRljr4i9vBENdZd/wfKAEPgRDjDVMoxhdi4R6
                    zCEdr3vdt8PNI7AbaO7N4znbT8ftmhtxs8+YlgmomSI4vu8FvkDk34xx4T0A6OCC
                    dy5pKr7I/0JbWjqjdb26wgDPhAL8Ts6wV6o23xNtAGgJhJ0=
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

    withConfigMap("auth-config", version = "6") {
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
                    "files.deleteFile:write"
                )
            ),
            TokenExtension(
                "_project-repository",
                listOf(
                    "files.updateProjectAcl:write"
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
                    "tokenExtension" to tokenExtension,
                    "unconditionalPasswordResetWhitelist" to listOf("_password-reset")
                )
            )
        )
    }
}
