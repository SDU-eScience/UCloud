package dk.sdu.cloud

import java.util.Base64

@JvmInline
value class Json(val encoded: String)

const val imDevImage = "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2024.1.0-dev-21"

sealed class PortAllocator {
    abstract fun allocate(port: Int): Int

    object Direct : PortAllocator() {
        override fun allocate(port: Int): Int = port
    }

    class Remapped(private val base: Int) : PortAllocator() {
        var portAllocator = base
        val allocatedPorts = HashMap<Int, Int>()

        override fun allocate(port: Int): Int {
            allocatedPorts[port] = portAllocator
            return portAllocator++
        }
    }
}

data class Environment(
    val name: String,
    val repoRoot: LFile,
    val doWriteFile: Boolean,
) {
    val dataDirectory = currentEnvironment.also { it.mkdirs() }

    fun createComposeFile(services: List<ComposeService>): LFile {
        disableRemoteFileWriting = !doWriteFile
        try {
            LoadingIndicator(if (doWriteFile) "Creating compose environment..." else "Initializing service list...").use {
                val builder = ComposeBuilder(this)
                with(builder) {
                    for (service in services) {
                        with(service) {
                            build()
                        }
                    }
                }

                val file = dataDirectory.child("docker-compose.yaml")
                file.writeText(builder.createComposeFile())
                return file
            }
        } finally {
            disableRemoteFileWriting = false
        }
    }
}

class ComposeBuilder(val environment: Environment) {
    val volumes = HashSet<String>()
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
            append("}, ")
            append(""" "volumes": { """)
            for ((index, vol) in volumes.withIndex()) {
                if (index != 0) append(", ")
                append(""" "$vol": {} """)
            }

            run {
                val prefix = (composeName ?: environment.name) + "_"
                volumes.forEach { allVolumeNames.add(prefix + it) }
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
        serviceConvention: Boolean,
        address: String? = null,
        uiHelp: String? = null,
    ) {
        services[name] = compose
        allServices.add(Service(name, title, logsSupported, execSupported, serviceConvention, address, uiHelp))
    }

    sealed class Provider : ComposeService() {
        abstract val name: String
        abstract val title: String
        open val canRegisterProducts: Boolean = true
        abstract fun install(credentials: ProviderCredentials)
    }

    companion object {
        fun providerFromName(name: String): ComposeService.Provider {
            return allProviders().find { it.name == name } ?: error("No such provider: $name")
        }

        fun allProviders(): List<Provider> = listOf(
            Kubernetes,
            Slurm,
            GoSlurm,
        )
    }

    object UCloudBackend : ComposeService() {
        override fun ComposeBuilder.build() {
            val logs = environment.dataDirectory.child("logs").also { it.mkdirs() }
            val homeDir = environment.dataDirectory.child("backend-home").also { it.mkdirs() }
            val configDir = environment.dataDirectory.child("backend-config").also { it.mkdirs() }
            val gradleDir = environment.dataDirectory.child("backend-gradle").also { it.mkdirs() }

            service(
                "backend",
                "UCloud/Core: Backend",
                Json(
                    //language=json
                    """
                      {
                        "image": "$imDevImage",
                        "command": ["sleep", "inf"],
                        "restart": "always",
                        "hostname": "backend",
                        "ports": [
                          "${portAllocator.allocate(8080)}:8080",
                          "${portAllocator.allocate(11412)}:11412"
                        ],
                        "volumes": [
                          "${environment.repoRoot}/backend:/opt/ucloud",
                          "${environment.repoRoot}/frontend-web/webclient:/opt/frontend",
                          "${logs.absolutePath}:/var/log/ucloud",
                          "${configDir.absolutePath}:/etc/ucloud",
                          "${homeDir.absolutePath}:/home",
                          "${gradleDir.absolutePath}:/root/.gradle"
                        ]
                      }
                    """.trimIndent(),
                ),
                serviceConvention = true
            )

            val postgresDataDir = environment.dataDirectory.child("pg-data").also { it.mkdirs() }

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
                          "${portAllocator.allocate(35432)}:5432"
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
                address = "https://postgres.localhost.direct",
                uiHelp = """
                    The postgres interface is connected to the database of UCloud/Core. You don't need any credentials. 
                    
                    If you wish to connect via psql or some tool:
                    
                    Hostname: localhost<br>
                    Port: 35432<br>
                    Database: postgres<br>
                    Username: postgres<br>
                    Password: postgrespassword
                """
            )

            val redisDataDir = environment.dataDirectory.child("redis-data").also { it.mkdirs() }
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
                        "volumes": [
                          "${environment.repoRoot}/frontend-web/webclient:/opt/ucloud"
                        ]
                      }
                    """.trimIndent()
                ),

                serviceConvention = false,
                address = "https://ucloud.localhost.direct",
                uiHelp = """
                    Default credentials to access UCloud:
                    
                    Username: user<br>
                    Password: mypassword<br>
                """
            )
        }
    }

    object Kubernetes : Provider() {
        override val name = "k8"
        override val title = "Kubernetes"

        override fun ComposeBuilder.build() {
            val k8Provider = environment.dataDirectory.child("k8").also { it.mkdirs() }
            val k3sDir = k8Provider.child("k3s").also { it.mkdirs() }
            val k3sOutput = k3sDir.child("output").also { it.mkdirs() }

            val k3sData = "k3sdata".also { volumes.add(it) }
            val k3sCni = "k3scni".also { volumes.add(it) }
            val k3sKubelet = "k3skubelet".also { volumes.add(it) }
            val k3sEtc = "k3setc".also { volumes.add(it) }

            val imDir = k8Provider.child("im").also { it.mkdirs() }
            val imGradle = imDir.child("gradle").also { it.mkdirs() }
            val imData = imDir.child("data").also { it.mkdirs() }
            val imStorage = imDir.child("storage").also { it.mkdirs() }
            listOf("home", "projects", "collections").forEach {
                imStorage.child(it).mkdirs()
            }
            val imLogs = environment.dataDirectory.child("logs").also { it.mkdirs() }

            val passwdDir = imDir.child("passwd").also { it.mkdirs() }
            val passwdFile = passwdDir.child("passwd")
            val groupFile = passwdDir.child("group")
            val shadowFile = passwdDir.child("shadow")
            if (!passwdFile.exists()) {
                passwdFile.writeText(
                    """
                        ucloud:x:998:998::/home/ucloud:/bin/sh
                        ucloudalt:x:11042:11042::/home/ucloudalt:/bin/sh
                    """.trimIndent()
                )
                groupFile.writeText(
                    """
                        ucloud:x:998:
                        ucloudalt:x:11042:
                    """.trimIndent()
                )

                shadowFile.writeText(
                    """
                        ucloud:!:19110::::::
                        ucloudalt:!:19110::::::
                    """.trimIndent()
                )
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
                          "${k3sOutput.absolutePath}:/output",
                          "${k3sData}:/var/lib/rancher/k3s",
                          "${k3sCni}:/var/lib/cni",
                          "${k3sKubelet}:/var/lib/kubelet",
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
                        "image": "$imDevImage",
                        "command": ["sleep", "inf"],
                        "hostname": "k8",
                        "volumes": [
                          "${imGradle.absolutePath}:/root/.gradle",
                          "${imData.absolutePath}:/etc/ucloud",
                          "${imLogs.absolutePath}:/var/log/ucloud",
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

            service(
                "k8pgweb",
                "K8 Provider: Postgres UI",
                Json(
                    //language=json
                    """
                      {
                        "image": "sosedoff/pgweb",
                        "hostname": "k8pgweb",
                        "restart": "always",
                        "environment": {
                          "DATABASE_URL": "postgres://postgres:postgrespassword@k8:5432/postgres?sslmode=disable"
                        }
                      }
                    """.trimIndent()
                ),
                serviceConvention = false,
                address = "https://k8-pg.localhost.direct",
            )
        }

        override fun install(credentials: ProviderCredentials) {
            val k8Provider = currentEnvironment.child("k8").also { it.mkdirs() }
            val imDir = k8Provider.child("im").also { it.mkdirs() }
            val imData = imDir.child("data").also { it.mkdirs() }

            val installMarker = imData.child(".install-marker")
            if (installMarker.exists()) return

            imData.child("core.yaml").writeText(
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

            imData.child("server.yaml").writeText(
                //language=yaml
                """
                    refreshToken: ${credentials.refreshToken}
                    envoy:
                      executable: /usr/bin/envoy
                      funceWrapper: false
                      directory: /var/run/ucloud/envoy
                      
                    database:
                      type: Embedded
                      directory: /etc/ucloud/pgsql
                      host: 0.0.0.0
                      password: postgrespassword
                """.trimIndent()
            )

            imData.child("ucloud_crt.pem").writeText(credentials.publicKey)

            imData.child("products.yaml").writeText(
                //language=yaml
                """
                    compute:
                      syncthing:
                        cost: { type: Free }
                        syncthing:
                          description: A product for use in syncthing
                          cpu: 1
                          memory: 1
                          gpu: 0
                      cpu:
                        cost: { type: Money }
                        template: 
                          cpu: [1, 2, 200]
                          memory: 1
                          description: An example CPU machine with 1 vCPU.
                          pricePerHour: 0.5
                      cpu-h:
                        cost: 
                          type: Resource 
                          interval: Minutely
                        template: 
                          cpu: [1, 2]
                          memory: 1
                          description: An example CPU machine with 1 vCPU.
                    storage: 
                      storage:
                          cost:
                            type: Resource
                            unit: GB
                          storage:
                            description: An example storage system
                          share:
                            description: This drive type is used for shares only.
                          project-home:
                            description: This drive type is used for member files of a project only.
                    publicLinks:
                      public-link:
                        cost: { type: Free }
                        public-link:
                          description: An example public link
                    publicIps:
                      public-ip:
                        cost:
                          type: Resource
                          unit: IP
                        public-ip:
                          description: A _fake_ public IP product
                    licenses:
                      license:
                        cost: { type: Resource }
                        license:
                          description: A _fake_ license
                          tags: ["fake", "license"]
                """.trimIndent()
            )

            imData.child("plugins.yaml").writeText(
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
                      kubernetes:
                        namespace: ucloud-apps
                      scheduler: Pods
                      developmentMode:
                        fakeIpMount: true
                        fakeMemoryAllocation: true
                        usePortForwarding: true
                  
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
                    "chown -R 11042:11042 /mnt/storage/*"
                ),
                tty = false
            ).streamOutput().executeToText()

            compose.exec(
                currentEnvironment,
                "k8",
                listOf("sh", "-c", """
                    while ! test -e "/mnt/k3s/kubeconfig.yaml"; do
                      sleep 1
                      echo "Waiting for Kubernetes to be ready..."
                    done
                """.trimIndent()),
                tty = false
            ).streamOutput().executeToText()

            compose.exec(
                currentEnvironment,
                "k8",
                listOf(
                    "sed",
                    "-i",
                    "s/127.0.0.1/k3/g",
                    "/mnt/k3s/kubeconfig.yaml"
                ),
                tty = false
            ).streamOutput().executeToText()

            compose.exec(
                currentEnvironment,
                "k8",
                listOf(
                    "kubectl",
                    "--kubeconfig",
                    "/mnt/k3s/kubeconfig.yaml",
                    "create",
                    "namespace",
                    "ucloud-apps"
                ),
                tty = false
            ).streamOutput().executeToText()

            compose.exec(
                currentEnvironment,
                "k8",
                listOf(
                    "sh",
                    "-c",
                    """
                        cat > /tmp/pvc.yml << EOF
                        ---
                        apiVersion: v1
                        kind: PersistentVolume
                        metadata:
                            name: cephfs
                            namespace: ucloud-apps
                        spec:
                            capacity:
                                storage: 1000Gi
                            volumeMode: Filesystem
                            accessModes:
                                - ReadWriteMany
                            persistentVolumeReclaimPolicy: Retain
                            storageClassName: ""
                            hostPath:
                                path: "/mnt/storage"
                        
                        ---
                        apiVersion: v1
                        kind: PersistentVolumeClaim
                        metadata:
                            name: cephfs
                            namespace: ucloud-apps
                        spec:
                            accessModes:
                                - ReadWriteMany
                            storageClassName: ""
                            volumeName: cephfs
                            resources:
                                requests:
                                    storage: 1000Gi
                        EOF
                    """.trimIndent()
                ),
                tty = false
            ).streamOutput().executeToText()

            compose.exec(
                currentEnvironment,
                "k8",
                listOf("kubectl", "--kubeconfig", "/mnt/k3s/kubeconfig.yaml", "create", "-f", "/tmp/pvc.yml"),
                tty = false
            ).streamOutput().executeToText()

            compose.exec(
                currentEnvironment,
                "k8",
                listOf("rm", "/tmp/pvc.yml"),
                tty = false
            ).streamOutput().executeToText()

            installMarker.writeText("done")
        }
    }

    object Slurm : Provider() {
        override val name = "slurm"
        override val title = "Slurm"

        // NOTE(Dan): Please keep this number relatively stable. This will break existing installations if it moves
        // around too much.
        const val numberOfSlurmNodes = 2

        override fun ComposeBuilder.build() {
            val slurmProvider = environment.dataDirectory.child("slurm").also { it.mkdirs() }

            val imDir = slurmProvider.child("im").also { it.mkdirs() }
            val imGradle = imDir.child("gradle").also { it.mkdirs() }
            val imData = imDir.child("data").also { it.mkdirs() }
            val imHome = imDir.child("home").also { it.mkdirs() }
            val imWork = imDir.child("work").also { it.mkdirs() }
            val imLogs = environment.dataDirectory.child("logs").also { it.mkdirs() }
            val imMySqlDb = "immysql".also { volumes.add(it) }
            val etcMunge = "etc_munge".also { volumes.add(it) }
            val etcSlurm = "etc_slurm".also { volumes.add(it) }
            val logSlurm = "log_slurm".also { volumes.add(it) }

            val passwdDir = imDir.child("passwd").also { it.mkdirs() }
            val passwdFile = passwdDir.child("passwd")
            val groupFile = passwdDir.child("group")
            val shadowFile = passwdDir.child("shadow")
            if (!passwdFile.exists()) {
                passwdFile.writeText(
                    """
                        ucloud:x:998:998::/home/ucloud:/bin/sh
                        ucloudalt:x:11042:11042::/home/ucloudalt:/bin/sh
                    """.trimIndent()
                )
                groupFile.writeText(
                    """
                        ucloud:x:998:
                        ucloudalt:x:11042:
                    """.trimIndent()
                )

                shadowFile.writeText(
                    """
                        ucloud:!:19110::::::
                        ucloudalt:!:19110::::::
                    """.trimIndent()
                )
            }

            service(
                "slurm",
                "Slurm Provider: Integration module",
                Json(
                    //language=json
                    """
                      {
                        "image": "$imDevImage",
                        "command": ["sleep", "inf"],
                        "hostname": "slurm",
                        "volumes": [
                          "${imGradle.absolutePath}:/root/.gradle",
                          "${imData.absolutePath}:/etc/ucloud",
                          "${imLogs.absolutePath}:/var/log/ucloud",
                          "${imHome.absolutePath}:/home",
                          "${imWork.absolutePath}:/work",
                          "${environment.repoRoot}/provider-integration/integration-module:/opt/ucloud",
                          "${environment.repoRoot}/provider-integration/integration-module/example-extensions/simple:/etc/ucloud/extensions",
                          "$etcSlurm:/etc/slurm-llnl",
                          "${passwdDir.absolutePath}:/mnt/passwd"
                        ],
                        "volumes_from": ["slurmdbd:ro"]
                      }
                    """.trimIndent(),
                ),
                serviceConvention = true
            )

            service(
                "mysql",
                "Slurm Provider: MySQL (SlurmDB)",
                Json(
                    //language=json
                    """
                      {
                        "image": "mysql:5.7",
                        "hostname": "mysql",
                        "environment": {
                          "MYSQL_RANDOM_ROOT_PASSWORD": "yes",
                          "MYSQL_DATABASE": "slurm_acct_db",
                          "MYSQL_USER": "slurm",
                          "MYSQL_PASSWORD": "password"
                        },
                        "volumes": [
                          "${imMySqlDb}:/var/lib/mysql"
                        ],
                        "restart": "always"
                      }
                    """.trimIndent()
                ),
                serviceConvention = false
            )

            service(
                "slurmdbd",
                "Slurm Provider: slurmdbd",
                Json(
                    //language=json
                    """
                      {
                        "image": "dreg.cloud.sdu.dk/ucloud-dev/slurm:2022.2.0",
                        "command": ["slurmdbd", "sshd", "user-sync"],
                        "hostname": "slurmdbd",
                        "volumes": [
                          "${passwdDir.absolutePath}:/mnt/passwd",
                          "${imHome}:/home",
                          "${imWork}:/work",
                          "${environment.repoRoot}/provider-integration/integration-module:/opt/ucloud",
                          "$etcMunge:/etc/munge",
                          "$etcSlurm:/etc/slurm",
                          "$logSlurm:/var/log/slurm"
                        ],
                        "depends_on": ["mysql"],
                        "restart": "always"
                      }
                    """.trimIndent()
                ),
                serviceConvention = false
            )

            service(
                "slurmctld",
                "Slurm Provider: slurmctld",
                Json(
                    //language=json
                    """
                      {
                        "image": "dreg.cloud.sdu.dk/ucloud-dev/slurm:2022.2.0",
                        "command": ["slurmctld", "sshd", "user-sync"],
                        "hostname": "slurmctld",
                        "volumes": [
                          "${passwdDir.absolutePath}:/mnt/passwd",
                          "${imHome}:/home",
                          "${imWork}:/work",
                          "${environment.repoRoot}/provider-integration/integration-module:/opt/ucloud",
                          "$etcMunge:/etc/munge",
                          "$etcSlurm:/etc/slurm",
                          "$logSlurm:/var/log/slurm"
                        ],
                        "depends_on": ["slurmdbd"],
                        "restart": "always"
                      }
                    """.trimIndent()
                ),
                serviceConvention = false
            )

            for (id in 1..numberOfSlurmNodes) {
                service(
                    "c$id",
                    "Slurm Provider: Compute node $id",
                    Json(
                        //language=json
                        """
                          {
                            "image": "dreg.cloud.sdu.dk/ucloud-dev/slurm:2022.2.0",
                            "command": ["slurmd", "sshd", "user-sync"],
                            "hostname": "c$id",
                            "volumes": [
                              "${passwdDir.absolutePath}:/mnt/passwd",
                              "${imHome}:/home",
                              "${imWork}:/work",
                              "${environment.repoRoot}/provider-integration/integration-module:/opt/ucloud",
                              "$etcMunge:/etc/munge",
                              "$etcSlurm:/etc/slurm",
                              "$logSlurm:/var/log/slurm"
                            ],
                            "depends_on": ["slurmctld"],
                            "restart": "always"
                          }
                        """.trimIndent(),
                    ),
                    serviceConvention = false
                )
            }

            service(
                "slurmpgweb",
                "Slurm Provider: Postgres UI",
                Json(
                    //language=json
                    """
                      {
                        "image": "sosedoff/pgweb",
                        "hostname": "slurmpgweb",
                        "restart": "always",
                        "environment": {
                          "DATABASE_URL": "postgres://postgres:postgrespassword@slurm:5432/postgres?sslmode=disable"
                        }
                      }
                    """.trimIndent()
                ),
                serviceConvention = false,
                address = "https://slurm-pg.localhost.direct",
            )
        }

        override fun install(credentials: ProviderCredentials) {
            val slurmProvider = currentEnvironment.child("slurm").also { it.mkdirs() }
            val imDir = slurmProvider.child("im").also { it.mkdirs() }
            val imData = imDir.child("data").also { it.mkdirs() }

            val installMarker = imData.child(".install-marker")
            if (installMarker.exists()) return

            imData.child("core.yaml").writeText(
                //language=yaml
                """
                    providerId: slurm
                    launchRealUserInstances: true
                    allowRootMode: false
                    developmentMode: true
                    disableInsecureFileCheckIUnderstandThatThisIsABadIdeaButSomeDevEnvironmentsAreBuggy: true
                    hosts:
                      ucloud:
                        host: backend
                        scheme: http
                        port: 8080
                      self:
                        host: slurm.localhost.direct
                        scheme: https
                        port: 443
                    cors:
                      allowHosts: ["ucloud.localhost.direct"]
                """.trimIndent()
            )

            imData.child("server.yaml").writeText(
                //language=yaml
                """
                    refreshToken: ${credentials.refreshToken}
                    envoy:
                      executable: /usr/bin/envoy
                      funceWrapper: false
                      directory: /var/run/ucloud/envoy
                    database:
                      type: Embedded
                      host: 0.0.0.0
                      directory: /etc/ucloud/pgsql
                      password: postgrespassword
                """.trimIndent()
            )

            imData.child("ucloud_crt.pem").writeText(credentials.publicKey)

            imData.child("products.yaml").writeText(
                //language=yaml
                """
                    compute:
                      cpu:
                        cost:
                          type: Resource
                          interval: Minutely
                          unit: Cpu
                        template: 
                          cpu: [1, 2, 200]
                          memory: 1
                          description: An example CPU machine with 1 vCPU.
                    storage: 
                      storage:
                          cost:
                            type: Resource
                            unit: GB
                          storage:
                            description: An example storage system
                """.trimIndent()
            )

            imData.child("plugins.yaml").writeText(
                //language=yaml
                """
                  connection:
                    type: UCloud
                    redirectTo: https://ucloud.localhost.direct
                    insecureMessageSigningForDevelopmentPurposesOnly: true
                    extensions:
                      onConnectionComplete: /etc/ucloud/extensions/ucloud-connection

                  allocations:
                    COMPUTE:
                      type: Extension
                      extensions:
                        onAllocationTotal: /etc/ucloud/extensions/on-compute-allocation

                  jobs:
                    default:
                      type: Slurm
                      matches: "*"
                      partition: normal
                      useFakeMemoryAllocations: true
                      terminal:
                        type: SSH
                        generateSshKeys: true
                      web:
                        type: Simple
                        domainPrefix: slurm-
                        domainSuffix: .localhost.direct

                  fileCollections:
                    default:
                      type: Posix
                      matches: "*"
                      accounting: /etc/ucloud/extensions/storage-du-accounting
                      extensions:
                        driveLocator: /etc/ucloud/extensions/drive-locator

                  files:
                    default:
                      type: Posix
                      matches: "*"

                  projects:
                    type: Simple
                    unixGroupNamespace: 42000
                    extensions:
                      all: /etc/ucloud/extensions/project-extension

                """.trimIndent()
            )

            for (attempt in 0 until 30) {
                val success = compose.exec(
                    currentEnvironment,
                    "slurmctld",
                    listOf(
                        "/usr/bin/sacctmgr",
                        "--immediate",
                        "add",
                        "cluster",
                        "name=linux"
                    ),
                    tty = false,
                ).allowFailure().streamOutput().executeToText().first != null

                if (success) break
                Thread.sleep(2_000)
            }

            // These are mounted into the container, but permissions are wrong
            compose.exec(
                currentEnvironment,
                "slurm",
                listOf(
                    "sh",
                    "-c",
                    "chmod 0755 -R /etc/ucloud/extensions",
                ),
                tty = false
            ).streamOutput().executeToText()

            // This is to avoid rebuilding the image when the Slurm configuration changes
            compose.exec(
                currentEnvironment,
                "slurmctld",
                listOf(
                    "cp",
                    "-v",
                    "/opt/ucloud/docker/slurm/slurm.conf",
                    "/etc/slurm"
                ),
                tty = false
            ).streamOutput().executeToText()

            // Restart slurmctld in case configuration file has changed
            compose.stop(currentEnvironment, "slurmctld").streamOutput().executeToText()
            compose.start(currentEnvironment, "slurmctld").streamOutput().executeToText()

            installMarker.writeText("done")
        }
    }

    object GoSlurm : Provider() {
        override val name = "go-slurm"
        override val title = "Slurm (Go test)"
        override val canRegisterProducts: Boolean = false

        override fun ComposeBuilder.build() {
            val provider = environment.dataDirectory.child("go-slurm").also { it.mkdirs() }

            val imDir = provider.child("im").also { it.mkdirs() }
            val imGradle = imDir.child("gradle").also { it.mkdirs() }
            val imData = imDir.child("data").also { it.mkdirs() }
            val imHome = imDir.child("home").also { it.mkdirs() }
            val imWork = imDir.child("work").also { it.mkdirs() }
            val imLogs = environment.dataDirectory.child("logs").also { it.mkdirs() }

            val passwdDir = imDir.child("passwd").also { it.mkdirs() }
            val passwdFile = passwdDir.child("passwd")
            val groupFile = passwdDir.child("group")
            val shadowFile = passwdDir.child("shadow")
            if (!passwdFile.exists()) {
                passwdFile.writeText(
                    """
                        ucloud:x:998:998::/home/ucloud:/bin/sh
                        ucloudalt:x:11042:11042::/home/ucloudalt:/bin/sh
                    """.trimIndent()
                )
                groupFile.writeText(
                    """
                        ucloud:x:998:
                        ucloudalt:x:11042:
                    """.trimIndent()
                )

                shadowFile.writeText(
                    """
                        ucloud:!:19110::::::
                        ucloudalt:!:19110::::::
                    """.trimIndent()
                )
            }

            service(
                "go-slurm",
                "Slurm (Go test)",
                Json(
                    //language=json
                    """
                      {
                        "image": "$imDevImage",
                        "command": ["sleep", "inf"],
                        "hostname": "go-slurm",
                        "init": true,
                        "volumes": [
                          "${imGradle.absolutePath}:/root/.gradle",
                          "${imData.absolutePath}:/etc/ucloud",
                          "${imLogs.absolutePath}:/var/log/ucloud",
                          "${imHome.absolutePath}:/home",
                          "${imWork.absolutePath}:/work",
                          "${environment.repoRoot}/provider-integration/im2:/opt/ucloud",
                          "${passwdDir.absolutePath}:/mnt/passwd"
                        ],
                      }
                    """.trimIndent(),
                ),
                serviceConvention = true
            )
        }

        override fun install(credentials: ProviderCredentials) {
            val slurmProvider = currentEnvironment.child("go-slurm").also { it.mkdirs() }
            val imDir = slurmProvider.child("im").also { it.mkdirs() }
            val imData = imDir.child("data").also { it.mkdirs() }

            val installMarker = imData.child(".install-marker")
            if (installMarker.exists()) return

            imData.child("core.yaml").writeText(
                //language=yaml
                """
                    providerId: slurm
                    launchRealUserInstances: true
                    allowRootMode: false
                    developmentMode: true
                    disableInsecureFileCheckIUnderstandThatThisIsABadIdeaButSomeDevEnvironmentsAreBuggy: true
                    hosts:
                      ucloud:
                        host: backend
                        scheme: http
                        port: 8080
                      self:
                        host: slurm.localhost.direct
                        scheme: https
                        port: 443
                    cors:
                      allowHosts: ["ucloud.localhost.direct"]
                """.trimIndent()
            )

            imData.child("server.yaml").writeText(
                //language=yaml
                """
                    refreshToken: ${credentials.refreshToken}
                    envoy:
                      executable: /usr/local/bin/getenvoy
                      funceWrapper: true
                      directory: /var/run/ucloud/envoy
                    database:
                      type: Embedded
                      host: 0.0.0.0
                      directory: /etc/ucloud/pgsql
                      password: postgrespassword
                """.trimIndent()
            )

            imData.child("ucloud_crt.pem").writeText(credentials.publicKey)

            imData.child("products.yaml").writeText(
                //language=yaml
                """
                    compute:
                      cpu:
                        cost:
                          type: Resource
                          unit: Cpu
                        template: 
                          cpu: [1, 2, 200]
                          memory: 1
                          description: An example CPU machine with 1 vCPU.
                    storage: 
                      storage:
                          cost:
                            type: Resource
                            unit: GB
                          storage:
                            description: An example storage system
                """.trimIndent()
            )

            imData.child("plugins.yaml").writeText(
                //language=yaml
                """
                  connection:
                    type: UCloud
                    redirectTo: https://ucloud.localhost.direct
                    insecureMessageSigningForDevelopmentPurposesOnly: true
                    extensions:
                      onConnectionComplete: /etc/ucloud/extensions/connection-complete
                    
                  jobs:
                    default:
                      type: Slurm
                      matches: "*"
                      partition: normal
                      useFakeMemoryAllocations: true
                      terminal:
                        type: SSH
                        generateSshKeys: true
                      web:
                        type: Simple
                        domainPrefix: slurm-
                        domainSuffix: .localhost.direct
                  
                  fileCollections:
                    default:
                      type: Posix
                      matches: "*"
                      extensions:
                        additionalCollections: /etc/ucloud/extensions/posix-drive-locator
                      
                  files:
                    default:
                      type: Posix
                      matches: "*"
                  
                  projects:
                    type: Simple
                    unixGroupNamespace: 42000
                    extensions:
                      all: /etc/ucloud/extensions/project-extension
                      
                """.trimIndent()
            )

            installMarker.writeText("done")
        }
    }

    object Gateway : ComposeService() {
        override fun ComposeBuilder.build() {
            val gatewayDir = environment.dataDirectory.child("gateway").also { it.mkdirs() }
            val gatewayData = gatewayDir.child("data").also { it.mkdirs() }
            val certificates = gatewayDir.child("certs").also { it.mkdirs() }

            // See https://get.localhost.direct for details about this. Base64 just to avoid showing up in search
            // results.
            certificates.child("tls.crt").writeBytes(
                Base64.getDecoder().decode(
                    Gateway::class.java.getResourceAsStream("/tlsc.txt")!!.readAllBytes()
                        .decodeToString().replace("\r", "").replace("\n", "")
                )
            )

            certificates.child("tls.key").writeBytes(
                Base64.getDecoder().decode(
                    Gateway::class.java.getResourceAsStream("/tlsk.txt")!!.readAllBytes()
                        .decodeToString().replace("\r", "").replace("\n", "")
                )
            )

            val gatewayConfig = gatewayDir.child("Caddyfile")
            gatewayConfig.writeText(
                """
                    {
                        order grpc_web before reverse_proxy
                    }

                    https://ucloud.localhost.direct {
                        grpc_web
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
                        reverse_proxy /avatar.AvatarService/* h2c://backend:11412
                    }
                    
                    https://postgres.localhost.direct {
                        reverse_proxy pgweb:8081
                    }
                   
                    https://k8.localhost.direct {
                        reverse_proxy k8:8889
                    }
                    
                    https://k8-pg.localhost.direct {
                        reverse_proxy k8pgweb:8081
                    }
                    
                    https://slurm.localhost.direct {
                        reverse_proxy slurm:8889
                    }
                    
                    https://slurm-pg.localhost.direct {
                        reverse_proxy slurmpgweb:8081
                    }
                    
                    *.localhost.direct {
                        tls /certs/tls.crt /certs/tls.key
                        
                        @k8apps {
                            header_regexp k8app Host ^k8-.*
                        }
                        reverse_proxy @k8apps k8:8889
                        
                        @slurmapps {
                            header_regexp slurmapp Host ^slurm-.*
                        }
                        reverse_proxy @slurmapps slurm:8889
                    }
                """.trimIndent()
            )

            service(
                "gateway",
                "Gateway",
                Json(
                    // NOTE: The gateway is from this repo with no changes:
                    // https://github.com/mholt/caddy-grpc-web
                    // language=json
                    """
                      {
                        "image": "dreg.cloud.sdu.dk/ucloud/caddy-gateway:1",
                        "restart": "always",
                        "volumes": [
                          "${gatewayData.absolutePath}:/data",
                          "${gatewayConfig.absolutePath}:/etc/caddy/Caddyfile",
                          "${certificates.absolutePath}:/certs"
                        ],
                        "ports": [
                          "${portAllocator.allocate(80)}:80",
                          "${portAllocator.allocate(443)}:443" 
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
