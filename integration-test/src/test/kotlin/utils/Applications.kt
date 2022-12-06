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

    suspend fun create() {
        ToolStore.create.call(
            Unit,
            adminClient.withHttpBody(
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
                ContentType("text", "yaml")
            )
        ).throwIfInternalOrBadRequest()

        AppStore.create.call(
            Unit,
            adminClient.withHttpBody(
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
                ContentType("text", "yaml")
            )
        ).throwIfInternalOrBadRequest()

        AppStore.create.call(
            Unit,
            adminClient.withHttpBody(
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
                ContentType("text", "yaml")
            )
        ).throwIfInternalOrBadRequest()

        figletTool = ToolStore.findByNameAndVersion.call(
            FindByNameAndVersion("figlet", "1.0.0"),
            adminClient
        ).orThrow()

        figletBatch = AppStore.findByNameAndVersion.call(
            FindApplicationAndOptionalDependencies("figlet", "1.0.0"),
            adminClient
        ).orThrow()

        figletLongRunning = AppStore.findByNameAndVersion.call(
            FindApplicationAndOptionalDependencies("long-running", "1.0.0"),
            adminClient
        ).orThrow()

        val apps = listOf(figletBatch, figletLongRunning)

        for (app in apps) {
            AppStore.setPublic.call(
                SetPublicRequest(app.metadata.name, app.metadata.version, true),
                adminClient
            ).orThrow()
        }
    }
}
