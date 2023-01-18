package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.throwIfInternalOrBadRequest
import dk.sdu.cloud.calls.client.withHttpBody
import dk.sdu.cloud.integration.adminClient
import io.ktor.http.*

suspend fun createTool(name: String, version: String) {
    val toolYaml = """
        ---
        tool: v1

        title: $name
        name: $name
        version: $version

        container: alpine:3

        authors:
        - Dan
          
        defaultTimeAllocation:
          hours: 1
          minutes: 0
          seconds: 0

        description: All
                   
        defaultNumberOfNodes: 1 
        defaultTasksPerNode: 1

        backend: DOCKER
    """.trimIndent()

    ToolStore.create.call(
        Unit,
        adminClient.withHttpBody(
            toolYaml,
            ContentType("text", "x-yaml").withCharset(Charsets.UTF_8)
        )
    ).orThrow()
}

suspend fun createApp(name: String, version: String, toolName: String, toolVersion: String, isPublic: Boolean) {
    val appYaml = """
        ---
        application: v1

        title: $name
        name: $name
        version: $version

        applicationType: BATCH

        allowMultiNode: true

        tool:
          name: $toolName
          version: $toolVersion

        authors:
        - Dan

        container:
          runAsRoot: true
         
        description: >
          Alpine!
         
        invocation:
        - sh
        - -c
        - >
          echo "Hello, World!";
          sleep 2;
          echo "How are you doing?";
          sleep 1;
          echo "This is just me writing some stuff for testing purposes!";
          sleep 1;
          seq 0 7200 | xargs -n 1 -I _ sh -c 'echo _; sleep 1';

        outputFileGlobs:
          - "*"
    """.trimIndent()

    AppStore.create.call(
        Unit,
        adminClient.withHttpBody(
            appYaml,
            ContentType("text", "x-yaml").withCharset(Charsets.UTF_8)
        )
    ).orThrow()

    if (isPublic) {
        AppStore.setPublic.call(
            SetPublicRequest(name, version, true),
            adminClient
        ).orThrow()
    }
}

object ApplicationTestData {
    lateinit var figletTool: Tool
    lateinit var figletBatch: ApplicationWithFavoriteAndTags
    lateinit var figletLongRunning: ApplicationWithFavoriteAndTags
    lateinit var slurmNativeTool: Tool
    lateinit var slurmNativeBatch: ApplicationWithFavoriteAndTags
    lateinit var slurmNativeLongRunning: ApplicationWithFavoriteAndTags

    private suspend fun createTool(name: String, version: String, toolYaml: String): Tool {
        ToolStore.create.call(
            Unit,
            adminClient.withHttpBody(
                toolYaml,
                ContentType("text", "yaml")
            )
        ).throwIfInternalOrBadRequest()

        return ToolStore.findByNameAndVersion.call(
            FindByNameAndVersion(name, version),
            adminClient
        ).orThrow()
    }

    private suspend fun createApp(name: String, version: String, appYaml: String): ApplicationWithFavoriteAndTags {
        AppStore.create.call(
            Unit,
            adminClient.withHttpBody(appYaml, ContentType("text", "yaml"))
        ).throwIfInternalOrBadRequest()

        AppStore.setPublic.call(
            SetPublicRequest(name, version, true),
            adminClient
        ).orThrow()

        return AppStore.findByNameAndVersion.call(
            FindApplicationAndOptionalDependencies(name, version),
            adminClient
        ).orThrow()
    }

    suspend fun create() {
        // ==========================================================
        // Docker based applications
        // ==========================================================
        run {
            figletTool = createTool(
                "figlet",
                "1.0.0",
                """
                    ---
                    tool: v1

                    title: Figlet

                    name: figlet
                    version: 1.0.0

                    container: truek/figlets:1.1.1

                    authors:
                    - Dan Sebastian Thrane <dthrane@imada.sdu.dk>

                    description: Tool for rendering text.

                    defaultTimeAllocation:
                      hours: 0
                      minutes: 1
                      seconds: 0

                    backend: DOCKER
                """.trimIndent(),
            )

            figletBatch = createApp(
                "figlet",
                "1.0.0",
                """
                    ---
                    application: v1

                    title: Figlet
                    name: figlet
                    version: 1.0.0

                    tool:
                      name: figlet
                      version: 1.0.0

                    authors:
                    - Dan Sebastian Thrane <dthrane@imada.sdu.dk>

                    description: >
                      Render some text with Figlet Docker!

                    invocation:
                    - figlet
                    - type: var
                      vars: text
                      
                    parameters:
                      text:
                        title: "Some text to render with figlet"
                        type: text
                    
                    allowAdditionalMounts: true
                    applicationType: WEB
                """.trimIndent(),
            )

            figletLongRunning = createApp(
                "long-running",
                "1.0.0",
                """
                    ---
                    application: v1
                    
                    title: long running
                    name: long-running
                    version: 1.0.0
                    
                    tool:
                      name: figlet
                      version: 1.0.0
                    
                    authors: ["Dan Sebasti2 Thrane"]
                    
                    description: Runs for a long time
                    
                    # We just count to a really big number
                    invocation:
                    - figlet-count
                    - 1000000000
                """.trimIndent(),
            )
        }

        // ==========================================================
        // Native (for Slurm) based applications
        // ==========================================================
        run {
            slurmNativeTool = createTool(
                "slurm-native",
                "1.0.0",
                """
                    ---
                    tool: v1
                    backend: "NATIVE"
                    
                    title: "slurm-native"
                    name: "slurm-native"
                    version: "1.0.0"
                    
                    authors: ["UCloud"]
                    description: "Slurm native tool"
                    
                    defaultNumberOfNodes: 1
                    defaultTasksPerNode: 1
                    requiredModules: []
                    supportedProviders: ["slurm"]
                """.trimIndent()
            )

            slurmNativeBatch = createApp(
                "slurm-native",
                "1.0.0",
                """
                    ---
                    application: v1
                    applicationType: WEB

                    title: "slurm-native"
                    name: "slurm-native"
                    version: "1.0.0"
                    
                    tool:
                      name: slurm-native
                      version: 1.0.0

                    authors: ["UCloud"]
                    description: Slurm native application

                    allowMultiNode: false
                    allowAdditionalMounts: true
                    
                    invocation:
                    - /bin/echo
                    - type: var
                      vars: text
                      
                    parameters:
                      text:
                        title: "Some text to render"
                        type: text
                """.trimIndent()
            )

            slurmNativeLongRunning = createApp(
                "slurm-native-long",
                "1.0.0",
                """
                    ---
                    application: v1
                    applicationType: WEB

                    title: "slurm-native-long"
                    name: "slurm-native-long"
                    version: "1.0.0"

                    tool:
                      name: slurm-native
                      version: 1.0.0

                    authors: ["UCloud"]
                    description: Slurm native application

                    allowMultiNode: false
                    allowAdditionalMounts: true
                    
                    
                    invocation:
                    - sh
                    - -c
                    - >
                      echo "Hello, World!";
                      sleep 2;
                      echo "How are you doing?";
                      sleep 1;
                      echo "This is just me writing some stuff for testing purposes!";
                      sleep 1;
                      seq 0 7200 | xargs -n 1 -I _ sh -c 'echo _; sleep 1';
                        
                """.trimIndent()
            )
        }
    }
}
