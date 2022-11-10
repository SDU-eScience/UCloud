package dk.sdu.cloud

import java.io.File

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

    fun createComposeFile(services: List<Service>): File {
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

sealed class Service {
    abstract fun ComposeBuilder.build()

    object UCloudBackend : Service() {
        override fun ComposeBuilder.build() {
            val logs = File(environment.dataDirectory, "logs").also { it.mkdirs() }
            val homeDir = File(environment.dataDirectory, "backend-home").also { it.mkdirs() }

            services["backend"] = Json(
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
                      "${homeDir.absolutePath}:/home"
                    ]
                  }
                """.trimIndent()
            )

            val postgresDataDir = File(environment.dataDirectory, "pg-data").also { it.mkdirs() }
            startProcessAndCollectToString(
                listOf(findDocker(), "run", "--rm", "-v", "$postgresDataDir:/data", "alpine:3", "chown", "999:999", "/data")
            ).also {
                repeat(10) { println() }
                println("exit: ${it.statusCode}")
                println("stdout: ${it.stdout}")
                println("stderr: ${it.stderr}")
                repeat(10) { println() }
            }

            services["postgres"] = Json(
                //language=json
                """
                  {
                    "image": "postgres:15.0",
                    "hostname": "postgres",
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
            )

            val redisDataDir = File(environment.dataDirectory, "redis-data").also { it.mkdirs() }
            services["redis"] = Json(
                //language=json
                """
                  {
                    "image": "redis:5.0.9",
                    "hostname": "redis",
                    "volumes": [
                      "${redisDataDir.absolutePath}:/data"
                    ]
                  }
                """.trimIndent()
            )
        }
    }

    object UCloudFrontend : Service() {
        override fun ComposeBuilder.build() {
            services["frontend"] = Json(
                //language=json
                """
                  {
                    "image": "node",
                    "command": ["sleep", "inf"],
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
            )
        }
    }

    object Kubernetes : Service() {
        override fun ComposeBuilder.build() {

        }
    }

    object Slurm : Service() {
        override fun ComposeBuilder.build() {

        }
    }

    object Puhuri : Service() {
        override fun ComposeBuilder.build() {

        }
    }

    object Gateway : Service() {
        override fun ComposeBuilder.build() {
            val gatewayDir = File(environment.dataDirectory, "gateway").also { it.mkdir() }
            val gatewayData = File(gatewayDir, "data").also { it.mkdir() }
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
                        reverse_proxy /api/* backend:8080
                        reverse_proxy /auth/* backend:8080
                        redir / /app/dashboard
                        
                        tls internal
                    }
                   
                    https://debugger.localhost.direct {
                        reverse_proxy / backend:42999
                        
                        tls internal
                    }
                    
                    https://k8.localhost.direct {
                        reverse_proxy / k8-provider:8889
                        
                        tls internal
                    }
                    
                    https://slurm.localhost.direct {
                        reverse_proxy / slurm-provider:8889
                        
                        tls internal
                    }
                """.trimIndent()
            )

            services["gateway"] = Json(
                // language=json
                """
                  {
                    "image": "caddy",
                    "volumes": [
                      "${gatewayData.absolutePath}:/data",
                      "${gatewayConfig.absolutePath}:/etc/caddy/Caddyfile"
                    ],
                    "ports": [
                      "${environment.portAllocator.allocate(80)}:80" 
                    ],
                    "hostname": "gateway"
                  }
                """.trimIndent()
            )
        }
    }
}