package dk.sdu.cloud

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Desktop
import java.io.File
import java.net.URI

object Commands {
    fun portForward() {
        initializeServiceList()
        val ports = (portAllocator as PortAllocator.Remapped).allocatedPorts
        val conn = (commandFactory as RemoteExecutableCommandFactory).connection

        val forward = ports.entries.joinToString(" ") { "-L ${it.key}:localhost:${it.value}" }
        postExecFile.appendText(
            """
                echo;
                echo;
                echo;
                echo "Please keep this window running. You will not be able to access any services without it."
                echo "This window needs to be restarted if you add any new providers or switch environment!"
                echo;
                echo "This command requires your local sudo password to enable port forwarding of privileged ports (80 and 443)."
                echo;
                echo;
                echo;
                sudo -E ssh -F ~/.ssh/config -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null $forward ${conn.username}@${conn.host} sleep inf  
            """.trimIndent()
        )
    }

    fun openUserInterface(serviceName: String) {
        val service = serviceByName(serviceName)
        val address = service.address
        val uiHelp = service.uiHelp
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            println("Unable to open web-browser. Open this URL in your own browser:")
            println(address)
        } else {
            Desktop.getDesktop().browse(URI(address))
        }

        if (uiHelp != null) {
            printExplanation(uiHelp)
        }
    }

    fun openLogs(serviceName: String) {
        val service = serviceByName(serviceName)
        if (service.useServiceConvention) {
            postExecFile.appendText(
                compose.exec(
                    currentEnvironment,
                    serviceName,
                    listOf("sh", "-c", "tail -F /tmp/service.log /var/log/ucloud/*.log")
                ).toBashScript()
            )
        } else {
            postExecFile.appendText(compose.logs(currentEnvironment, serviceName).toBashScript())
        }
    }
    
    fun openShell(serviceName: String) {
        postExecFile.appendText(
            compose.exec(currentEnvironment, serviceName, listOf("/bin/sh", "-c", "bash || sh")).toBashScript()
        )
    }
    
    fun createProvider(providerName: String) {
        startProviderService(providerName)

        var credentials: ProviderCredentials? = null
        LoadingIndicator("Registering provider with UCloud/Core").use {
            credentials = registerProvider(providerName, providerName, 8889)
        }

        val creds = credentials!!

        LoadingIndicator("Configuring provider...").use {
            ComposeService.providerFromName(providerName).install(creds)
        }

        LoadingIndicator("Starting provider...").use {
            compose.up(currentEnvironment, noRecreate = true).executeToText()
            startService(serviceByName(providerName)).executeToText()
        }

        LoadingIndicator("Registering products with UCloud/Core").use {
            compose.exec(
                currentEnvironment,
                providerName,
                listOf("sh", "-c", """
                    while ! test -e "/var/run/ucloud/ucloud.sock"; do
                      sleep 1
                      echo "Waiting for UCloud/IM to be ready..."
                    done
                """.trimIndent()),
                tty = false,
            ).streamOutput().executeToText()

            compose.exec(
                currentEnvironment,
                providerName,
                listOf("sh", "-c", "yes | ucloud products register"),
                tty = false,
            ).also {
                it.deadlineInMillis = 30_000
            }.streamOutput().executeToText()
        }

        LoadingIndicator("Restarting provider...").use {
            stopService(serviceByName(providerName)).executeToText()
            startService(serviceByName(providerName)).executeToText()
        }

        LoadingIndicator("Granting credits to provider project").use {
            val accessToken = fetchAccessToken()
            val productPage = defaultMapper.decodeFromString(
                JsonObject.serializer(),
                callService(
                    "backend",
                    "GET",
                    "http://localhost:8080/api/products/browse?filterProvider=${providerName}&itemsPerPage=250",
                    accessToken
                ) ?: error("Failed to retrieve products from UCloud")
            )

            val productItems = productPage["items"] as JsonArray
            val productCategories = HashSet<String>()
            productItems.forEach { item ->
                productCategories.add(
                    (((item as JsonObject)["category"] as JsonObject)["name"] as JsonPrimitive).content
                )
            }

            productCategories.forEach { category ->
                callService(
                    "backend",
                    "POST",
                    "http://localhost:8080/api/accounting/rootDeposit",
                    accessToken,
                    //language=json
                    """
                      {
                        "items": [
                          {
                            "categoryId": { "name": "$category", "provider": "${providerName}" },
                            "amount": 50000000000,
                            "description": "Root deposit",
                            "recipient": {
                              "type": "project",
                              "projectId": "${creds.projectId}"
                            }
                          }
                        ]
                      }
                    """.trimIndent()
                )
            }
        }
    }

    fun serviceStart(serviceName: String) {
        postExecFile.appendText(
            startService(serviceByName(serviceName)).toBashScript()
        )
    }

    fun serviceStop(serviceName: String) {
        postExecFile.appendText(stopService(serviceByName(serviceName)).toBashScript())
    }

    fun environmentStop() {
        LoadingIndicator("Shutting down virtual cluster...").use {
            compose.down(currentEnvironment).streamOutput().executeToText()
        }
    }

    fun environmentStart() {
        startCluster(compose, noRecreate = false)
    }

    fun environmentRestart() {
        environmentStop()
        environmentStart()
    }

    fun environmentDelete(shutdown: Boolean = true) {
        if (shutdown) {
            LoadingIndicator("Shutting down virtual cluster...").use {
                compose.down(currentEnvironment, deleteVolumes = true).streamOutput().executeToText()
                if (compose is DockerCompose.Plugin) {
                    allVolumeNames.forEach { volName ->
                        ExecutableCommand(listOf(findDocker(), "volume", "rm", volName, "-f")).streamOutput()
                            .executeToText()
                    }
                }
            }
        }

        LoadingIndicator("Deleting files associated with virtual cluster...").use {
            // NOTE(Dan): Running this in a docker container to make sure we have permissions to
            // delete the files. This is basically a convoluted way of asking for root permissions
            // without actually asking for root permissions (we are just asking for the equivalent
            // through docker)
            try {
                ExecutableCommand(
                    listOf(
                        findDocker(),
                        "run",
                        "--rm",
                        "-v",
                        "${File(currentEnvironment.absolutePath).parentFile.absolutePath}:/data",
                        "alpine:3",
                        "/bin/sh",
                        "-c",
                        "rm -rf /data/${currentEnvironment.name}"
                    )
                ).also { if (!shutdown) it.allowFailure() }.executeToText()
            } catch (ex: Throwable) {
                if (shutdown) throw ex
            }

            File(localEnvironment.jvmFile.parentFile, "current.txt").delete()
            localEnvironment.jvmFile.deleteRecursively()
        }
    }

    fun environmentStatus() {
        postExecFile.appendText(compose.ps(currentEnvironment).toBashScript())
    }

    fun importApps() {
        LoadingIndicator("Importing applications").use {
            callService(
                "backend",
                "POST",
                "http://localhost:8080/api/hpc/apps/devImport",
                fetchAccessToken(),
                // NOTE(Dan): checksum is sha256 of the file
                """
                    {
                        "endpoint": "https://launcher-assets.cloud.sdu.dk/apps.zip",
                        "checksum": "a5b02af4f63acd060c3664b9c12f7ca9543893c0ae2f9bb8731d51d6efaef7ce"
                    }
                """.trimIndent()
            ) ?: error("Failed to import applications (see backend logs). " +
                    "You might need to do a git pull to get the latest version.")
        }
    }

    fun createSnapshot(snapshotName: String) {
        initializeServiceList()

        LoadingIndicator("Creating snapshot...").use {
            allServices
                .filter { it.useServiceConvention }
                .forEach { service ->
                    compose.exec(
                        currentEnvironment,
                        service.containerName,
                        listOf("/opt/ucloud/service.sh", "snapshot", snapshotName),
                        tty = false
                    ).allowFailure().streamOutput().executeToText()
                }
        }
    }

    fun restoreSnapshot(snapshotName: String) {
        initializeServiceList()

        LoadingIndicator("Restoring snapshot...").use {
            allServices
                .filter { it.useServiceConvention }
                .forEach { service ->
                    compose.exec(
                        currentEnvironment,
                        service.containerName,
                        listOf("/opt/ucloud/service.sh", "restore", snapshotName),
                        tty = false
                    ).allowFailure().streamOutput().executeToText()
                }
        }
    }
}
