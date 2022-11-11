package dk.sdu.cloud

import java.io.File
import java.util.Base64

@JvmInline
value class Json(val encoded: String)

sealed class PortAllocator {
    abstract fun allocate(port: Int): Int

    object Direct : PortAllocator() {
        override fun allocate(port: Int): Int = port
    }

    class Remapped(private val base: Int) : PortAllocator() {
        override fun allocate(port: Int): Int {
            return (port + base) % Short.MAX_VALUE
        }
    }
}

data class Environment(
    val name: String,
    val portAllocator: PortAllocator,
) {
    val repoRoot = run {
        when {
            File(".git").exists() -> File(".")
            File("../.git").exists() -> File("..")
            else -> error("Unable to determine repository root. Please run this script from the root of the repository.")
        }
    }.absoluteFile.normalize()

    val dataDirectory = File(repoRoot, ".compose/$name").also { it.mkdirs() }

    fun createComposeFile(services: List<ComposeService>): File {
        val builder = ComposeBuilder(this)
        with(builder) {
            for (service in services) {
                with(service) {
                    build()
                }
            }
        }

        val file = File(dataDirectory, "docker-compose.yaml")
        file.writeText(builder.createComposeFile())
        return file
    }
}

class ComposeBuilder(val environment: Environment) {
    val services = HashMap<String, Json>()

    fun createComposeFile(): String {
        return StringBuilder().apply {
            append("""{ "version": "3.9", "services": {""")
            for ((index, service) in services.entries.withIndex()) {
                if (index != 0) append(", ")
                append('"')
                append(service.key)
                append('"')
                append(":")
                append(service.value.encoded)
            }
            append("} }")
        }.toString()
    }
}

sealed class ComposeService {
    abstract fun ComposeBuilder.build()

    fun ComposeBuilder.service(
        name: String,
        title: String,
        compose: Json,
        logsSupported: Boolean = true,
        execSupported: Boolean = true,
        serviceConvention: Boolean = true,
        address: String? = null
    ) {
        services[name] = compose
        allServices.add(Service(name, title, logsSupported, execSupported, serviceConvention, address))
    }

    object UCloudBackend : ComposeService() {
        override fun ComposeBuilder.build() {
            val logs = File(environment.dataDirectory, "logs").also { it.mkdirs() }
            val homeDir = File(environment.dataDirectory, "backend-home").also { it.mkdirs() }
            val configDir = File(environment.dataDirectory, "backend-config").also { it.mkdirs() }
            val gradleDir = File(environment.dataDirectory, "backend-gradle").also { it.mkdirs() }

            service(
                "backend",
                "UCloud/Core: Backend",
                Json(
                    //language=json
                    """
                      {
                        "image": "dreg.cloud.sdu.dk/ucloud/ucloud-dev:2021.3.0-alpha14",
                        "command": ["sleep", "inf"],
                        "restart": "always",
                        "hostname": "backend",
                        "ports": [
                          "${environment.portAllocator.allocate(8080)}:8080",
                          "${environment.portAllocator.allocate(42999)}:42999"
                        ],
                        "volumes": [
                          "${environment.repoRoot}/backend:/opt/ucloud",
                          "${environment.repoRoot}/debugger:/opt/debugger",
                          "${environment.repoRoot}/frontend-web/webclient:/opt/frontend",
                          "${logs.absolutePath}:/var/log/ucloud",
                          "${configDir.absolutePath}:/etc/ucloud",
                          "${homeDir.absolutePath}:/home",
                          "${gradleDir.absolutePath}:/root/.gradle"
                        ]
                      }
                    """.trimIndent(),
                ),
            )

            val postgresDataDir = File(environment.dataDirectory, "pg-data").also { it.mkdirs() }

            service(
                "postgres",
                "UCloud/Core: Postgres",
                Json(
                    //language=json
                    """
                      {
                        "image": "postgres:15.0",
                        "hostname": "postgres",
                        "restart": "always",
                        "environment": {
                          "POSTGRES_PASSWORD": "postgrespassword"
                        },
                        "volumes": [
                          "${postgresDataDir.absolutePath}:/var/lib/postgresql/data",
                          "${environment.repoRoot}/backend:/opt/ucloud"
                        ],
                        "ports": [
                          "${environment.portAllocator.allocate(35432)}:5432"
                        ]
                      }
                    """.trimIndent()
                ),

                serviceConvention = false,
            )

            service(
                "pgweb",
                "UCloud/Core: Postgres UI",
                Json(
                    //language=json
                    """
                      {
                        "image": "sosedoff/pgweb",
                        "hostname": "pgweb",
                        "restart": "always",
                        "environment": {
                          "DATABASE_URL": "postgres://postgres:postgrespassword@postgres:5432/postgres?sslmode=disable"
                        }
                      }
                    """.trimIndent()
                ),
                serviceConvention = false,
                address = "https://postgres.localhost.direct"
            )

            val redisDataDir = File(environment.dataDirectory, "redis-data").also { it.mkdirs() }
            service(
                "redis",
                "UCloud/Core: Redis",
                Json(
                    //language=json
                    """
                      {
                        "image": "redis:5.0.9",
                        "hostname": "redis",
                        "restart": "always",
                        "volumes": [
                          "${redisDataDir.absolutePath}:/data"
                        ]
                      }
                    """.trimIndent()
                ),

                serviceConvention = false
            )
        }
    }

    object UCloudFrontend : ComposeService() {
        override fun ComposeBuilder.build() {
            service(
                "frontend",
                "UCloud/Core: Frontend",
                Json(
                    //language=json
                    """
                      {
                        "image": "node",
                        "command": ["sh", "-c", "npm install ; npm run start:compose"],
                        "restart": "always",
                        "hostname": "frontend",
                        "working_dir": "/opt/ucloud",
                        "ports": [
                          "${environment.portAllocator.allocate(9000)}:9000"
                        ],
                        "volumes": [
                          "${environment.repoRoot}/frontend-web/webclient:/opt/ucloud"
                        ]
                      }
                    """.trimIndent()
                ),

                address = "https://ucloud.localhost.direct",
                serviceConvention = false
            )
        }
    }

    object Kubernetes : ComposeService() {
        override fun ComposeBuilder.build() {

        }
    }

    object Slurm : ComposeService() {
        override fun ComposeBuilder.build() {

        }
    }

    object Puhuri : ComposeService() {
        override fun ComposeBuilder.build() {

        }
    }

    object Gateway : ComposeService() {
        override fun ComposeBuilder.build() {
            val gatewayDir = File(environment.dataDirectory, "gateway").also { it.mkdirs() }
            val gatewayData = File(gatewayDir, "data").also { it.mkdirs() }
            val certificates = File(gatewayDir, "certs").also { it.mkdirs() }

            // See https://get.localhost.direct for details about this. Base64 just to avoid showing up in search
            // results.
            File(certificates, "tls.crt").writeBytes(
                Base64.getDecoder().decode(
                    Gateway::class.java.getResourceAsStream("/tlsc.txt")!!.readAllBytes()
                        .decodeToString().replace("\r", "").replace("\n", "")
                )
            )

            File(certificates, "tls.key").writeBytes(
                Base64.getDecoder().decode(
                    Gateway::class.java.getResourceAsStream("/tlsk.txt")!!.readAllBytes()
                        .decodeToString().replace("\r", "").replace("\n", "")
                )
            )

            val gatewayConfig = File(gatewayDir, "Caddyfile")
            gatewayConfig.writeText(
                """
                    https://ucloud.localhost.direct {
                        reverse_proxy /api/auth-callback-csrf frontend:9000
                        reverse_proxy /api/auth-callback frontend:9000
                        reverse_proxy /api/sync-callback frontend:9000
                        reverse_proxy /assets frontend:9000
                        reverse_proxy /favicon.ico frontend:9000
                        reverse_proxy /favicon.svg frontend:9000
                        reverse_proxy /AppVersion.txt frontend:9000
                        reverse_proxy /Images/* frontend:9000
                        reverse_proxy /app frontend:9000
                        reverse_proxy /app/* frontend:9000
                        reverse_proxy /@* frontend:9000
                        reverse_proxy /node_modules/* frontend:9000
                        reverse_proxy /site.config.json frontend:9000
                        reverse_proxy /api/* backend:8080
                        reverse_proxy /auth/* backend:8080
                        reverse_proxy / frontend:9000
                    }
                    
                    https://postgres.localhost.direct {
                        reverse_proxy pgweb:8081
                    }
                   
                    https://debugger.localhost.direct {
                        reverse_proxy backend:42999
                    }
                    
                    https://k8.localhost.direct {
                        reverse_proxy k8-provider:8889
                    }
                    
                    https://slurm.localhost.direct {
                        reverse_proxy slurm-provider:8889
                    }
                    
                    *.localhost.direct {
                        tls /certs/tls.crt /certs/tls.key
                    }
                """.trimIndent()
            )

            service(
                "gateway",
                "Gateway",
                Json(
                    // language=json
                    """
                      {
                        "image": "caddy",
                        "restart": "always",
                        "volumes": [
                          "${gatewayData.absolutePath}:/data",
                          "${gatewayConfig.absolutePath}:/etc/caddy/Caddyfile",
                          "${certificates.absolutePath}:/certs"
                        ],
                        "ports": [
                          "${environment.portAllocator.allocate(80)}:80",
                          "${environment.portAllocator.allocate(443)}:443" 
                        ],
                        "hostname": "gateway"
                      }
                    """.trimIndent()
                ),

                serviceConvention = false
            )
        }
    }
}