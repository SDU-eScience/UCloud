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

    sealed class Provider : ComposeService() {
        abstract fun install(credentials: ProviderCredentials)
    }

    companion object {
        fun providerFromName(name: String): ComposeService.Provider {
            return when (name) {
                "k8" -> ComposeService.Kubernetes
                "slurm" -> ComposeService.Slurm
                "puhuri" -> ComposeService.Puhuri
                else -> ComposeService.GenericProvider(name)
            }
        }
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

    object Kubernetes : Provider() {
        override fun ComposeBuilder.build() {
            val k8Provider = File(environment.dataDirectory, "k8").also { it.mkdirs() }
            val k3sDir = File(k8Provider, "k3s").also { it.mkdirs() }
            val k3sOutput = File(k3sDir, "output").also { it.mkdirs() }
            val k3sData = File(k3sDir, "data").also { it.mkdirs() }
            val k3sCni = File(k3sDir, "cni").also { it.mkdirs() }
            val k3sKubelet = File(k3sDir, "kubelet").also { it.mkdirs() }
            val k3sEtc = File(k3sDir, "etc").also { it.mkdirs() }
            val k3sTmp = File(k3sDir, "tmp").also { it.mkdirs() }

            val imDir = File(k8Provider, "im").also { it.mkdirs() }
            val imGradle = File(imDir, "gradle").also { it.mkdirs() }
            val imData = File(imDir, "data").also { it.mkdirs() }
            val imStorage = File(imDir, "storage").also { it.mkdirs() }
            listOf("home", "projects", "collections").forEach {
                File(imStorage, it).mkdir()
            }
            val imLogs = File(environment.dataDirectory, "logs").also { it.mkdirs() }

            val passwdDir = File(imDir, "passwd").also { it.mkdirs() }
            val passwdFile = File(passwdDir, "passwd")
            val groupFile = File(passwdDir, "group")
            val shadowFile = File(passwdDir, "shadow")
            if (!passwdFile.exists()) {
                passwdFile.writeText("ucloud:x:998:998::/home/ucloud:/bin/sh\n")
                groupFile.writeText("ucloud:x:998:\n")
                shadowFile.writeText("ucloud:!:19110::::::\n")
            }

            service(
                "k3",
                "K8 Provider: K3s Node",
                Json(
                    //language=json
                    """
                      {
                        "image": "rancher/k3s:v1.21.6-rc2-k3s1",
                        "privileged": true,
                        "tmpfs": ["/run", "/var/run"],
                        "environment": [
                          "K3S_KUBECONFIG_OUTPUT=/output/kubeconfig.yaml",
                          "K3S_KUBECONFIG_MODE=666"
                        ],
                        "command": ["server"],
                        "hostname": "k3",
                        "restart": "always",
                        "volumes": [
                          "${k3sTmp.absolutePath}:/tmp",
                          "${k3sOutput.absolutePath}:/output",
                          "${k3sData.absolutePath}:/var/lib/rancher/k3s",
                          "${k3sCni.absolutePath}:/var/lib/cni",
                          "${k3sKubelet.absolutePath}:/var/lib/kubelet",
                          "${k3sEtc}:/etc/rancher",
                          "${imStorage.absolutePath}:/mnt/storage"
                        ]
                      }
                    """.trimIndent()
                ),
                serviceConvention = false
            )

            service(
                "k8",
                "K8 Provider: Integration module",
                Json(
                    //language=json
                    """
                      {
                        "image": "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2022.2.0",
                        "command": ["sleep", "inf"],
                        "volumes": [
                          "${imGradle.absolutePath}:/root/.gradle",
                          "${imData.absolutePath}:/etc/ucloud",
                          "${imLogs.absolutePath}:/var/logs/ucloud",
                          "${k3sOutput.absolutePath}:/mnt/k3s",
                          "${imStorage.absolutePath}:/mnt/storage",
                          "${environment.repoRoot}/provider-integration/integration-module:/opt/ucloud",
                          "${passwdDir.absolutePath}:/mnt/passwd"
                        ]
                      }
                    """.trimIndent(),
                ),
                serviceConvention = true
            )
        }

        override fun install(credentials: ProviderCredentials) {
            val k8Provider = File(currentEnvironment, "k8").also { it.mkdirs() }
            val imDir = File(k8Provider, "im").also { it.mkdirs() }
            val imData = File(imDir, "data").also { it.mkdirs() }

            val installMarker = File(imData, ".install-marker")
            if (installMarker.exists()) return

            File(imData, "core.yaml").writeText(
                //language=yaml
                """
                    providerId: k8
                    launchRealUserInstances: false
                    allowRootMode: true
                    developmentMode: true
                    hosts:
                      ucloud:
                        host: backend
                        scheme: http
                        port: 8080
                      self:
                        host: k8.localhost.direct
                        scheme: https
                        port: 443
                    cors:
                      allowHosts: ["ucloud.localhost.direct"]
                """.trimIndent()
            )

            File(imData, "server.yaml").writeText(
                //language=yaml
                """
                    refreshToken: ${credentials.refreshToken}
                """.trimIndent()
            )

            File(imData, "ucloud_crt.pem").writeText(credentials.publicKey)

            File(imData, "products.yaml").writeText(
                //language=yaml
                """
                    compute:
                      cpu:
                        - name: cpu-1
                          description: An example CPU machine with 1 vCPU.
                          cpu: 1
                          memoryInGigs: 1
                          gpu: 0
                          cost:
                            currency: DKK
                            frequency: MINUTE
                            price: 0.001666
                            
                        - name: cpu-2
                          description: An example CPU machine with 2 vCPU.
                          cpu: 2
                          memoryInGigs: 2
                          gpu: 0
                          cost:
                            currency: DKK
                            frequency: MINUTE
                            price: 0.003332
                            
                    storage: 
                      storage:
                        - name: storage
                          description: An example storage system
                          cost:
                            quota: true
                        - name: share
                          description: This drive type is used for shares only.
                          cost:
                            quota: true
                        - name: project-home
                          description: This drive type is used for member files of a project only.
                          cost:
                            quota: true
                            
                    ingress:
                      public-link:
                        - name: public-link 
                          description: An example public link
                          cost:
                            currency: FREE
                            
                    publicIps:
                      public-ip:
                        - name: public-ip
                          description: A _fake_ public IP product
                          cost:
                            quota: true
                """.trimIndent()
            )

            File(imData, "plugins.yaml").writeText(
                //language=yaml
                """
                  connection:
                    type: UCloud
                    redirectTo: https://ucloud.localhost.direct
                    insecureMessageSigningForDevelopmentPurposesOnly: true
                    
                  jobs:
                    default:
                      type: UCloud
                      matches: "*"
                      kubeConfig: /mnt/k3s
                      namespace: ucloud-apps
                      scheduler: Pods
                      fakeIpMount: true
                      forceMinimumReservation: true
                  
                  fileCollections:
                    default:
                      type: UCloud
                      matches: "*"
                      
                  files:
                    default:
                      type: UCloud
                      matches: "*"
                      mountLocation: "/mnt/storage"
                  
                  ingresses:
                    default:
                      type: UCloud
                      matches: "*"
                      domainPrefix: k8-app-
                      domainSuffix: .localhost.direct
                      
                  publicIps:
                    default:
                      type: UCloud
                      matches: "*"
                      iface: dummy
                      gatewayCidr: null
                      
                  licenses:
                    default:
                      type: Generic
                      matches: "*"
                      
                  shares:
                    default:
                      type: UCloud
                      matches: "*"
                      
                """.trimIndent()
            )

            compose.exec(
                currentEnvironment,
                "k8",
                listOf(
                    "sh",
                    "-c",
                    // NOTE(Dan): We have to use the UID/GID here instead of UCloud since the user hasn't been
                    // created yet.
                    "chown -R 998:998 /mnt/storage/*"
                ),
                tty = false
            ).executeToText()

            println("TODO Make sure Kubernetes works")

            installMarker.writeText("done")
        }
    }

    object Slurm : Provider() {
        override fun ComposeBuilder.build() {

        }

        override fun install(credentials: ProviderCredentials) {

        }
    }

    object Puhuri : Provider() {
        override fun ComposeBuilder.build() {

        }

        override fun install(credentials: ProviderCredentials) {

        }
    }

    class GenericProvider(val name: String) : Provider() {
        override fun ComposeBuilder.build() {

        }

        override fun install(credentials: ProviderCredentials) {

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
                        reverse_proxy k8:8889
                    }
                    
                    https://slurm.localhost.direct {
                        reverse_proxy slurm:8889
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