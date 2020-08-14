//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

import java.io.File
import java.util.*

bundle { ctx ->
    name = "auth"
    version = "1.28.0"

    val host = config<String>("host", "Host name (no schema). E.g. 'cloud.sdu.dk'")
    val scheme = config<String>("scheme", "Scheme (http or https)", "https")
    val trustLocalhost = config<Boolean>("trustLocalhost", "Should we trust the localhost origin?", false)
    val certificate = config<String>("cert", "Public certificate for JWT validation (PEM)")
    val enableWayf = config<Boolean>("enableWayf", "Should WAYF be enabled?", false)

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
        // This code no longer works
        /*
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
        */
    }

    withCronJob(deployment, "0 2 * * 1", listOf("--tokenScan")) {}

    withSecret("auth-wayf", version = "0") {
        println("auth-wayf must be configured! (Enter to continue)")
        Scanner(System.`in`).nextLine()
    }

    withSecret("auth-wayf-config", version = "0") {
        println("auth-wayf-config must be configured! (Enter to continue)")
        Scanner(System.`in`).nextLine()
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
        val dbSchema = dbUser
        val generatedPassword = UUID.randomUUID().toString()

        executeStatement("create user \"$dbUser\" password '$generatedPassword';")
        executeStatement("create schema \"$dbSchema\" authorization \"$dbUser\";")

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
        addConfig(
            "rpc.yml",
            //language=yml
            """
                rpc:
                  client:
                    host:
                      host: ${host}
                      scheme: ${scheme}
            """.trimIndent()
        )

        addConfig(
            "tokenvalidation.yml",
            mapOf<String, Any?>(
                "tokenValidation" to mapOf<String, Any?>(
                    "jwt" to mapOf<String, Any?>(
                        "publicCertificate" to certificate
                    )
                )
            )
        )
    }

    withConfigMap("auth-config", version = "6") {
        val trustedOrigins = ArrayList<String>().apply {
            add(host)
            if (trustLocalhost) add("localhost")
        }

        data class AuthService(
            val name: String,
            val endpoint: String,
            val serviceMode: String = "WEB",
            val endpointAcceptStateViaCookie: Boolean = false,
            val refreshTokenExpiresAfter: Long = 2592000000
        )

        val base = "${scheme}://${host}"
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
