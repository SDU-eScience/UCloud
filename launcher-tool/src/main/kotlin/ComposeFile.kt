package dk.sdu.cloud

import java.util.Base64

@JvmInline
value class Json(val encoded: String)

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
        abstract fun install(credentials: ProviderCredentials)
    }

    companion object {
        fun providerFromName(name: String): ComposeService.Provider {
            return allProviders().find { it.name == name } ?: error("No such provider: $name")
        }

        fun allProviders(): List<Provider> = listOf(
            Kubernetes,
            Slurm,
            Puhuri
        )
    }

    object UCloudBackend : ComposeService() {
        override fun ComposeBuilder.build() {
            val logs = environment.dataDirectory.child("logs").also { it.mkdirs() }
            val homeDir = environment.dataDirectory.child("backend-home").also { it.mkdirs() }
            val configDir = environment.dataDirectory.child("backend-config").also { it.mkdirs() }
            val gradleDir = environment.dataDirectory.child("backend-gradle").also { it.mkdirs() }
            val debuggerGradle = environment.dataDirectory.child("debugger-gradle").also { it.mkdirs() }

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
                          "${portAllocator.allocate(8080)}:8080",
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
                serviceConvention = true
            )

            service(
                "debugger",
                "UCloud/Core: Debugger",
                Json(
                    //language=json
                    """
                      {
                        "image": "dreg.cloud.sdu.dk/ucloud/ucloud-dev:2021.3.0-alpha14",
                        "command": ["sleep", "inf"],
                        "restart": "always",
                        "hostname": "debugger",
                        "ports": [
                          "${portAllocator.allocate(42999)}:42999"
                        ],
                        "volumes": [
                          "${environment.repoRoot}/debugger:/opt/ucloud",
                          "${logs.absolutePath}:/var/log/ucloud",
                          "${debuggerGradle.absolutePath}:/root/.gradle"
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
                        "image": "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2022.2.68",
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
                """.trimIndent()
            )

            imData.child("ucloud_crt.pem").writeText(credentials.publicKey)

            imData.child("products.yaml").writeText(
                //language=yaml
                """
                    compute:
                      syncthing:
                        - name: syncthing
                          description: A product for use in syncthing
                          cpu: 1
                          memoryInGigs: 1
                          gpu: 0
                          cost:
                            currency: FREE
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
                      namespace: ucloud-apps
                      scheduler: Pods
                      fakeIpMount: true
                      forceMinimumReservation: true
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
                        "image": "dreg.cloud.sdu.dk/ucloud-dev/integration-module:2022.2.68",
                        "command": ["sleep", "inf"],
                        "hostname": "slurm",
                        "volumes": [
                          "${imGradle.absolutePath}:/root/.gradle",
                          "${imData.absolutePath}:/etc/ucloud",
                          "${imLogs.absolutePath}:/var/log/ucloud",
                          "${imHome.absolutePath}:/home",
                          "${imWork.absolutePath}:/work",
                          "${environment.repoRoot}/provider-integration/integration-module:/opt/ucloud",
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
                """.trimIndent()
            )

            imData.child("ucloud_crt.pem").writeText(credentials.publicKey)

            imData.child("products.yaml").writeText(
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

            val imExtensions = imData.child("extensions").also { it.mkdirs() }

            imExtensions.child("connection-complete").writeText(
                """
                    #!/usr/bin/env python3
                    import json
                    import sys
                    import subprocess
                    import os

                    if os.getuid() != 0:
                        res = subprocess.run(['sudo', '-S', sys.argv[0], sys.argv[1]], stdin=open('/dev/null'))
                        if res.returncode != 0:
                            print("ucloud-extension failed. Is sudo misconfigured?")
                            exit(1)
                        exit(0)

                    request = json.loads(open(sys.argv[1]).read())
                    username: str = request['username']

                    local_username = username.replace('-', '').replace('#', '').replace('@', '')

                    response = {}

                    def lookup_user() -> int:
                        id_result = subprocess.run(['/usr/bin/id', '-u', local_username], stdout=subprocess.PIPE)
                        if id_result.returncode != 0:
                            return None
                        else:
                            return int(id_result.stdout)

                    uid = lookup_user()
                    if uid != None:
                        # User already exists. In that case we want to simply return the appropiate ID.
                        response['uid'] = uid
                        response['gid'] = uid # TODO(Dan): This is overly simplified
                    else:
                        # We need to create a user.
                        useradd_result = subprocess.run(['/usr/sbin/useradd', '-G', 'ucloud', '-m', local_username], stdout=subprocess.PIPE,
                                                        stderr=subprocess.PIPE)
                        if useradd_result.returncode != 0:
                            print("Failed to create a user!")
                            print(useradd_result.stdout)
                            print(useradd_result.stderr)
                            exit(1)
                        
                        uid = lookup_user()
                        if uid == None:
                            print("Failed to create a user! Could not look it up after calling useradd.")
                            exit(1)

                        response['uid'] = uid
                        response['gid'] = uid

                    print(json.dumps(response))
                """.trimIndent()
            )

            imExtensions.child("posix-drive-locator").writeText(
                """
                    #!/usr/bin/env python3
                    import sys
                    import subprocess
                    import json

                    # =====================================================================================================================
                    # Utilities
                    # =====================================================================================================================

                    def get_group_by_gid(gid):
                        result = subprocess.run(['/usr/bin/getent', 'group', str(gid)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        if result.returncode != 0:
                            return None
                        return result.stdout.decode('UTF-8').split(':')[0]

                    def get_username_by_uid(uid):
                        result = subprocess.run(['/usr/bin/getent', 'passwd', str(uid)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        if result.returncode != 0:
                            return None
                        return result.stdout.decode('UTF-8').split(':')[0]

                    # =====================================================================================================================
                    # Loading request
                    # =====================================================================================================================

                    request = json.loads(open(sys.argv[1]).read())
                    owner = request
                    owner_type = owner['type']

                    # =====================================================================================================================
                    # Mapping
                    # =====================================================================================================================

                    if owner_type == 'user':
                        username = get_username_by_uid(owner['uid'])
                        response = {
                            'title' : 'Home',
                            'path' : f'/home/{username}'
                        }
                        print(json.dumps([response]))

                    elif owner_type == 'project':
                        group_name = get_group_by_gid(owner['gid'])
                        response = {
                            'title' : 'Work',
                            'path' : f'/work/{group_name}'
                        }
                        print(json.dumps([response]))

                    else:
                        print(f'Unknown owner type {owner_type}')
                        exit(1)
     
                """.trimIndent()
            )

            imExtensions.child("project-extension").writeText(
                """
                    #!/usr/bin/env python3
                    import json
                    import sys
                    import subprocess
                    import os
                    import re

                    # NOTE(Dan): This script requires root privileges. However, the integration module will launch it with the privileges
                    # of the ucloud service user. As a result, we immediately attempt to elevate our own privileges via `sudo`.
                    if os.getuid() != 0:
                        res = subprocess.run(['sudo', '-S', sys.argv[0], sys.argv[1]], stdin=open('/dev/null'))
                        if res.returncode != 0:
                            print("project-extension failed. Is sudo misconfigured?")
                            exit(1)
                        exit(0)

                    ########################################################################################################################

                    request = json.loads(open(sys.argv[1]).read())
                    request_type = request['type']

                    ########################################################################################################################

                    def generate_name(ucloud_title, allocated_gid):
                        return re.sub(r'[^a-z0-9]', '_', ucloud_title.lower()) + '_' + str(allocated_gid)

                    def create_group(gid, name):
                        result = subprocess.run(['/usr/sbin/groupadd', '-g', str(gid), name], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        success = result.returncode == 0
                        if success:
                            subprocess.run(['mkdir', '-p', f'/work/{name}'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                            subprocess.run(['chgrp', name, f'/work/{name}'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                            subprocess.run(['chmod', '770', f'/work/{name}'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        return success

                    def rename_group(gid, name):
                        result = subprocess.run(['/usr/sbin/groupmod', '-n', name, get_group_by_gid(gid)], stdout=subprocess.PIPE,
                                                stderr=subprocess.PIPE)
                        return result.returncode == 0

                    def delete_group(gid):
                        group_name = get_group_by_gid(gid)
                        if group_name is None:
                            return False
                        result = subprocess.run(['/usr/sbin/groupdel', get_group_by_gid(gid)], stdout=subprocess.PIPE,
                                                stderr=subprocess.PIPE)
                        return result.returncode == 0

                    def add_user_to_group(uid, gid):
                        if get_group_by_gid(gid) is None:
                            print("{} error: Non-existing group with id {}".format(request_type,gid))
                            exit(1)

                        if get_username_by_uid(uid) is None:
                            print("{} error: Non-existing user with id {}".format(request_type,uid))
                            exit(1)

                        result = subprocess.run(['/usr/sbin/usermod', '-a', '-G', get_group_by_gid(gid), get_username_by_uid(uid)],
                                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        return result.returncode == 0

                    def remove_user_from_group(uid, gid):
                        result = subprocess.run(['/usr/bin/gpasswd', '-d', get_username_by_uid(uid), get_group_by_gid(gid)],
                                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        return result.returncode == 0

                    def get_gid_by_group(group_name):
                        result = subprocess.run(['/usr/bin/getent', 'group', group_name], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        if result.returncode != 0:
                            return None
                        return int(result.stdout.decode('UTF-8').split(':')[2])

                    def get_group_by_gid(gid):
                        result = subprocess.run(['/usr/bin/getent', 'group', str(gid)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        if result.returncode != 0:
                            return None
                        return result.stdout.decode('UTF-8').split(':')[0]

                    def get_username_by_uid(uid):
                        result = subprocess.run(['/usr/bin/getent', 'passwd', str(uid)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                        if result.returncode != 0:
                            return None
                        return result.stdout.decode('UTF-8').split(':')[0]

                    ########################################################################################################################

                    if request_type == 'project_renamed':
                        gid = request['newProject']['localId']
                        if request['oldProject'] is None:
                            create_group(gid, generate_name(request['newTitle'], gid))
                        else:
                            rename_group(gid, generate_name(request['newTitle'], gid))

                    elif request_type == 'members_added_to_project':
                        gid = request['newProject']['localId']
                        create_group(gid, generate_name(request['newProject']['project']['specification']['title'], gid))
                        for member in request['newMembers']:
                            uid = member['uid']
                            if uid is None: continue
                            add_user_to_group(uid, gid)

                    elif request_type == 'members_removed_from_project':
                        gid = request['newProject']['localId']
                        create_group(gid, generate_name(request['newProject']['project']['specification']['title'], gid))
                        for member in request['removedMembers']:
                            uid = member['uid']
                            if uid is None: continue
                            remove_user_from_group(uid, gid)

                    print('{}')
                                        
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

            compose.stop(currentEnvironment, "slurmctld")
            compose.start(currentEnvironment, "slurmctld")

            compose.exec(
                currentEnvironment,
                "slurm",
                listOf(
                    "sh",
                    "-c",
                    "chmod 755 /etc/ucloud/extensions /etc/ucloud/extensions/*"
                ),
                tty = false
            )

            installMarker.writeText("done")
        }
    }

    object Puhuri : Provider() {
        override val name = "puhuri"
        override val title = "Puhuri"

        override fun ComposeBuilder.build() {

        }

        override fun install(credentials: ProviderCredentials) {

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
                        reverse_proxy debugger:42999
                    }
                    
                    https://k8.localhost.direct {
                        reverse_proxy k8:8889
                    }
                    
                    https://slurm.localhost.direct {
                        reverse_proxy slurm:8889
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
