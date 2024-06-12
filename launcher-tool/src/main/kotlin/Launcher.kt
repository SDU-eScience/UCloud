package dk.sdu.cloud

import de.codeshelf.consoleui.prompt.ConsolePrompt
import jline.TerminalFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.system.exitProcess

val prompt = ConsolePrompt()
lateinit var compose: DockerCompose
lateinit var commandFactory: ExecutableCommandFactory
lateinit var fileFactory: FileFactory
lateinit var postExecFile: File
lateinit var repoRoot: LocalFile

fun ExecutableCommand(
    args: List<String>,
    workingDir: LFile? = null,
    postProcessor: (result: ProcessResultText) -> String = { it.stdout },
    allowFailure: Boolean = false,
    deadlineInMillis: Long = 1000 * 60 * 5,
): ExecutableCommand = commandFactory.create(args, workingDir, postProcessor, allowFailure, deadlineInMillis)

fun cliHint(invocation: String) {
    println(ansi().reset().render("You can also do this with: '")
        .bold().render("./launcher $invocation").boldOff().render("'"))
}

var isHeadless: Boolean = false

fun main(args: Array<String>) {
    try {
        val postExecPath = args.getOrNull(0) ?: error("Bad invocation")
        postExecFile = File(postExecPath)

        if (args.getOrNull(1) == "--help") {
            printHelp()
        }

        AnsiConsole.systemInstall()

        val repoRoot = run {
            when {
                File(".git").exists() -> File(".")
                File("../.git").exists() -> File("..")
                else -> error("Unable to determine repository root. Please run this script from the root of the repository.")
            }
        }.absoluteFile.normalize()

        dk.sdu.cloud.repoRoot = LocalFile(repoRoot.absolutePath)

        val version = runCatching { File(repoRoot, "./backend/version.txt").readText() }.getOrNull()?.trim() ?: ""

        if (args.contains("--version") || args.contains("-v")) {
            println("UCloud $version")
            exitProcess(0)
        }

        println("UCloud $version - Launcher tool")
        println(CharArray(TerminalFactory.get().width) { '-' }.concatToString())

        // NOTE(Dan): initCurrentEnvironment() needs these to be set. We start out by running locally.
        commandFactory = LocalExecutableCommandFactory()
        fileFactory = LocalFileFactory()

        val shouldInitializeTestEnvironment = (args.contains("init") && args.contains("--all-providers"))

        isHeadless = shouldInitializeTestEnvironment || (args.contains("env") && args.contains("delete")) ||
             (args.contains("snapshot") && args.find { it.contains(Regex("^[t][0-9]+\$")) } != null)

        // NOTE(Dan): initCurrentEnvironment() now initializes an environment which is ready. It returns true if the
        // environment is new. This method will override several "environment" global variables, such as the
        // commandFactory and fileFactory.
        val shouldStart = initCurrentEnvironment(shouldInitializeTestEnvironment).shouldStartEnvironment

        // NOTE(Dan): We start out by trying to understand if we need to start the environment. This is more or less
        // just checking the output of `docker compose ps`. This has the added benefit of stopping early if the user
        // doesn't have docker compose.
        val compose = findCompose()
        dk.sdu.cloud.compose = compose

        val (psText, failureText) = compose.ps(currentEnvironment).executeToText()
        if (psText == null && !shouldStart) {
            println("Unable to start docker compose in ${currentEnvironment}!")
            println()
            println(failureText)
            println()
            println("The error message above we got from docker compose. If this isn't helpful, " +
                "then try deleting this directory: ${currentEnvironment}.")
            exitProcess(1)
        }

        val psLines =
            (psText ?: "").lines().filter { it.isNotBlank() && !it.trim().startsWith("name", ignoreCase = true) && !it.trim().startsWith("---") }

        if (shouldStart || psLines.size <= 1) {
            generateComposeFile()
            val startConfirmed = shouldInitializeTestEnvironment || confirm(
                prompt,
                "The environment '${currentEnvironment.name}' is not running. Do you want to start it?",
                default = true
            )

            if (!startConfirmed) return

            startCluster(compose, noRecreate = false)

            if (shouldStart) {
                LoadingIndicator("Retrieving initial access token").use {
                    for (attempt in 1..10) {
                        val success = runCatching { fetchAccessToken() }.isSuccess
                        if (success) break

                        Thread.sleep(1000)
                    }
                }

                Commands.importApps()

                println()
                println()
                println("UCloud is now running. You should create a provider to get started. Select the " +
                    "'Create provider...' entry below to do so.")
            }
        }

        if (shouldInitializeTestEnvironment) {
            val providers = ComposeService.allProviders()
            for (provider in providers) {
                Commands.createProvider(provider.name)
            }
        }

        cliIntercept(args.drop(1))

        val topLevel = TopLevelMenu()
        when (topLevel.display(prompt)) {
            topLevel.portforward -> {
                cliHint("port-forward")
                Commands.portForward()
            }

            topLevel.openUserInterface -> {
                initializeServiceList()
                Commands.openUserInterface(ServiceMenu(requireAddress = true).display(prompt).name)
            }

            topLevel.openLogs -> {
                initializeServiceList()
                val item = ServiceMenu(requireExec = true).display(prompt)
                cliHint("svc ${item.name} logs")
                Commands.openLogs(item.name)
            }

            topLevel.openShell -> {
                initializeServiceList()
                val item = ServiceMenu(requireExec = true).display(prompt)
                cliHint("svc ${item.name} sh")
                Commands.openShell(item.name)
            }

            topLevel.createProvider -> {
                generateComposeFile()
                syncRepository()

                while (true) {
                    val configured = listConfiguredProviders()
                    val selectedProviders = CreateProviderMenu.display(prompt)
                        .filter { it.name !in configured && !it.disabled }

                    if (selectedProviders.isEmpty()) {
                        println("You didn't select any providers. Use space to select a provider and enter to finish.")
                        println("Alternatively, you can exit with crtl + c.")
                        println()
                        continue
                    }

                    for (provider in selectedProviders) {
                        Commands.createProvider(provider.name)
                    }

                    break
                }
            }

            topLevel.services -> {
                generateComposeFile()
                syncRepository()
                val service = serviceByName(ServiceMenu().display(prompt).name)

                when (ServiceActionMenu.display(prompt)) {
                    ServiceActionMenu.start -> {
                        cliHint("svc ${service.containerName} start")
                        Commands.serviceStart(service.containerName)
                    }

                    ServiceActionMenu.stop -> {
                        cliHint("svc ${service.containerName} stop")
                        Commands.serviceStop(service.containerName)
                    }

                    ServiceActionMenu.restart -> {
                        cliHint("svc ${service.containerName} restart [--follow]")
                        Commands.serviceStop(service.containerName)
                        Commands.serviceStart(service.containerName)
                    }
                }
            }

            topLevel.test -> {
                generateComposeFile()
                syncRepository()
                println("Not yet implemented") // TODO
            }

            topLevel.environment -> {
                generateComposeFile()
                syncRepository()

                when (EnvironmentMenu.display(prompt)) {
                    EnvironmentMenu.stop -> {
                        cliHint("env stop")
                        Commands.environmentStop()
                    }

                    EnvironmentMenu.restart -> {
                        cliHint("env restart")
                        Commands.environmentRestart()
                    }

                    EnvironmentMenu.delete -> {
                        val shouldDelete = confirm(
                            prompt,
                            "Are you sure you want to permanently delete the environment and all the data?"
                        )

                        if (shouldDelete) {
                            cliHint("env delete")
                            Commands.environmentDelete()
                        }
                    }

                    EnvironmentMenu.status -> {
                        cliHint("env status")
                        Commands.environmentStatus()
                    }

                    EnvironmentMenu.switch -> {
                        LoadingIndicator("Shutting down virtual cluster...").use {
                            compose.down(currentEnvironment).streamOutput().executeToText()
                        }

                        val baseDir = File(localEnvironment.jvmFile, ".compose").also { it.mkdirs() }
                        val env = selectOrCreateEnvironment(baseDir, false)
                        initIO(true)
                        currentEnvironment.child("..").child(env).mkdirs()
                        File(baseDir, "current.txt").writeText(env)
                    }
                }
            }

            topLevel.help -> {
                val topics = object : Menu("Select a topic") {
                    val gettingStarted = item("start", "Getting started")
                    val usage = item("usage", "Using UCloud and basic troubleshooting")
                    val debug = item("debug", "UCloud/Core databases and debugging")
                    val providers = item("providers", "UCloud/IM and Providers")
                }

                var nextTopic: ListItem? = topics.display(prompt)

                while (nextTopic != null) {
                    nextTopic = null

                    when (topics.display(prompt)) {
                        topics.gettingStarted -> {
                            println(
                                """
                                    Welcome! This is a small interactive help tool. For more in-depth documentation, 
                                    please go here: https://docs.cloud.sdu.dk
                                    
                                    UCloud should now be up and running. If everything is working, then you should be able
                                    to access UCloud's frontend by opening the following web-page in your browser:
                                    https://ucloud.localhost.direct.
                                    
                                    The default credentials for this instance is:
                                    
                                    Username: user
                                    Password: mypassword
                                    
                                    This is an UCloud admin account which you can use to manage the UCloud instance. You
                                    can see which options are available to you from the "Admin" tab in the UCloud sidebar.
                                """.trimIndent()
                            )

                            val moreTopics = object : Menu("Select a topic") {
                                val troubleshoot = item("troubleshoot", "It is not working")
                                val whatNext = item("whatNext", "What should I do now?")
                            }

                            when (moreTopics.display(prompt)) {
                                moreTopics.troubleshoot -> {
                                    println(
                                        """
                                            There are a number of ways for you to troubleshoot a UCloud environment which
                                            is not working. Below we will try to guide you through a number of steps which
                                            might be relevant.
                                        """.trimIndent()
                                    )

                                    fun suggestion(text: String) {
                                        println()
                                        println(text)
                                        println()
                                    }

                                    if (environmentIsRemote) {
                                        suggestion(
                                            """
                                                It looks like your current environment is using a remote machine. Please 
                                                make sure that port-forwarding is configured and working correctly. You can 
                                                access port-forwarding from the interactive menu.
                                            """.trimIndent()
                                        )
                                    }

                                    suggestion(
                                        """
                                            Sometimes Docker is not able to correctly restart all the container of your
                                            system. You can try to restart your environment with "./launcher env restart".
                                            
                                            Following this, you should attempt to verify that the backend is running and
                                            not producing any errors. You can view the logs with "./launcher svc backend logs".
                                        """.trimIndent()
                                    )

                                    suggestion(
                                        """
                                            Some development builds can be quite buggy or incompatible between versions. 
                                            This can, for example, happen when in-development database schemas change 
                                            drastically. In these cases, it is often easier to simply recreate your 
                                            development environment. You can do this by running "./launcher env delete".
                                        """.trimIndent()
                                    )

                                    suggestion(
                                        """
                                            If the issue persist after recreating your environment, then you might want to
                                            troubleshoot further by looking at the logs of the backend (./launcher svc backend logs)
                                            and any configured providers. Finally, you may wish to inspect the 
                                            database (https://postgres.localhost.direct) and look for any problems here.
                                        """.trimIndent()
                                    )
                                }

                                moreTopics.whatNext -> {
                                    nextTopic = topics.usage
                                }
                            }
                        }

                        topics.usage -> {
                            println(
                                """
                                    UCloud has quite a number of features. If you have never used UCloud before, then
                                    we recommend reading the documentation here: https://docs.cloud.sdu.dk
                                    
                                    You can also select one of the topics below for common operations.
                                """.trimIndent()
                            )

                            val moreTopics = object : Menu("Select a topic") {
                                val login = item("login", "How do I login?")
                                val createUser = item("createUser", "How do I create a new user?")
                                val createProject = item("createProject", "How do I create a project?")
                                val certExpired = item("certExpired", "The certificate has expired?")
                                val noProducts = item("noProducts", "I don't see any machines when I attempt to start a job")
                                val noFiles = item("noFiles", "I don't see any drives when I attempt to access my files")
                            }

                            when (moreTopics.display(prompt)) {
                                moreTopics.login -> {
                                    println("""
                                        You can access UCloud here: https://ucloud.localhost.direct
                                        
                                        Username: user
                                        Password: mypassword
                                        
                                        See the "Getting started" topic for more information.
                                    """.trimIndent())
                                }

                                moreTopics.createUser -> {
                                    println(
                                        """
                                            Using your admin user. You can select the "Admin" tab in the sidebar and
                                            create a new user from the "Create user" sub-menu.
                                        """.trimIndent()
                                    )
                                }

                                moreTopics.createProject -> {
                                    println(
                                        """
                                            You can create a new sub-project from the "Root" project.
                                            
                                            1. Click on "Manage projects" from the project selector.
                                               You can find this next to the UCloud logo after you have logged in.
                                            2. Open the "Root" project by clicking on "Root" 
                                               (alternatively, right click and select properties)
                                            3. Open the "Subprojects" menu
                                            4. Click "Create subproject" in the left sidebar
                                            
                                            At this point you should have a new project. Remember, your project cannot
                                            do anything until you have allocated resources to it. You should 
                                            automatically end up on a screen where you can ask for resources from your
                                            configured providers. If you don't see any resources, then make sure that
                                            you have configured a provider and that they are running.
                                        """.trimIndent()
                                    )
                                }

                                moreTopics.certExpired -> {
                                    println(
                                        """
                                            Try pulling from the git repository again. If that doesn't help and you
                                            are part of the development slack, try to ask @dan.
                                        """.trimIndent()
                                    )
                                }

                                moreTopics.noFiles, moreTopics.noProducts -> {
                                    println(
                                        """
                                            Make sure that you have configured a provider and that it is running.
                                            You can create a provider by selecting "Create provider..." from the launcher
                                            menu.
                                            
                                            You can view the logs of the provider using "Open logs..." from the launcher.
                                        """.trimIndent()
                                    )

                                    println(
                                        """
                                            You should also make sure that your current workspace has resources from
                                            this provider.
                                            
                                            You can check this by looking at "Resource Allocations" on the dashboard.
                                            If you don't see your provider listed here, then you don't have any 
                                            resources allocated in this workspace.
                                            
                                            The easiest way to solve this, is by switching to the provider project. You
                                            can find this project from the project selector, which is next to the UCloud
                                            logo.
                                            
                                            If none of this works, then you may wish to look at the troubleshooting
                                            options from the "Getting started" help topic.
                                        """.trimIndent()
                                    )
                                }
                            }
                        }

                        topics.debug -> {
                            println(
                                """
                                    You can access the database from the web-interface here:
                                    https://postgres.localhost.direct.
                                    
                                    Alternatively, you can access it directly via:
                                    
                                    Host: localhost
                                    Port: 35432
                                    Username: postgres
                                    Password: postgrespassword
                                    
                                    You can view the logs from the database with: "./launcher svc postgres logs"
                                """.trimIndent()
                            )
                            println()
                        }

                        topics.providers -> {
                            println("TODO(17/11/22): Not yet written.")
                        }
                    }
                }
            }
        }
    } finally {
        TerminalFactory.get().restore()
    }
}

fun stopService(
    service: Service,
): ExecutableCommand {
    if (service.useServiceConvention) {
        return compose.exec(
            currentEnvironment,
            service.containerName,
            listOf("/opt/ucloud/service.sh", "stop"),
            tty = false
        )
    } else {
        return compose.stop(currentEnvironment, service.containerName)
    }
}

fun initializeServiceList() {
    generateComposeFile(doWriteFile = false)
}

fun generateComposeFile(doWriteFile: Boolean = true) {
    val providers = listConfiguredProviders()

    Environment(
        currentEnvironment.name,
        currentEnvironment.child("../../"),
        doWriteFile
    ).createComposeFile(
        buildList {
            add(ComposeService.UCloudBackend)
            add(ComposeService.UCloudFrontend)
            add(ComposeService.Gateway)
            for (provider in providers) {
                add(ComposeService.providerFromName(provider))
            }
        },
    )
}

@Serializable
data class AccessTokenWrapper(val accessToken: String)

@Serializable
data class BulkResponse<T>(val responses: List<T>)

@Serializable
data class FindByStringId(val id: String)

fun callService(
    container: String,
    method: String,
    url: String,
    bearer: String,
    body: String? = null,
    opts: List<String> = emptyList()
): String? {
    return compose.exec(
        currentEnvironment,
        container,
        buildList {
            add("curl")
            add("-ssS")
            add("-X$method")
            add(url)
            add("-H")
            add("Authorization: Bearer $bearer")
            if (body != null) {
                add("-H")
                add("Content-Type: application/json")
                add("-d")
                add(body)
            } else {
                add("-d")
                add("")
            }
            addAll(opts)
        },
        tty = false
    ).allowFailure().executeToText().first
}

fun startProviderService(providerId: String) {
    addProvider(providerId)
    generateComposeFile()
    LoadingIndicator("Starting provider services...").use {
        compose.up(currentEnvironment, noRecreate = true).executeToText()
    }
}

data class ProviderCredentials(val publicKey: String, val refreshToken: String, val projectId: String)
fun registerProvider(providerId: String, domain: String, port: Int): ProviderCredentials {
    val accessToken = fetchAccessToken()

    val projectId = defaultMapper.decodeFromString(
        BulkResponse.serializer(FindByStringId.serializer()),
        callService(
            "backend",
            "POST",
            "http://localhost:8080/api/projects/v2",
            accessToken,
            //language=json
            """
              {
                "items": [
                  {
                    "parent": null,
                    "title": "Provider $providerId",
                    "canConsumeResources": false
                  }
                ]
              }
            """.trimIndent(),
            opts = listOf(
                "-H", "principal-investigator: user"
            )
        ) ?: error("Project creation failed. Check backend logs.")
    ).responses.single().id

    callService(
        "backend",
        "POST",
        "http://localhost:8080/api/grants/v2/updateRequestSettings",
        accessToken,
        //language=json
        """
          {
            "enabled": true,
            "description": "An example grant allocator allocating for $providerId",
            "allowRequestsFrom": [{ "type":"anyone" }],
            "excludeRequestsFrom": [],
            "templates": {
              "type": "plain_text",
              "personalProject": "Please describe why you are applying for resources",
              "newProject": "Please describe why you are applying for resources",
              "existingProject": "Please describe why you are applying for resources"
            }
          }
        """.trimIndent(),

        opts = listOf(
            "-H", "Project: $projectId"
        )
    ) ?: error("Failed to mark project as grant giver")

    callService(
        "backend",
        "POST",
        "http://localhost:8080/api/providers",
        accessToken,
        //language=json
        """
          {
            "items": [
              {
                "id": "$providerId",
                "domain": "$domain",
                "https": false,
                "port": $port
              }
            ]
          }
        """.trimIndent(),

        opts = listOf(
            "-H", "Project: $projectId"
        )
    ) ?: error("Provider creation failed. Check backend logs.")

    val providerObject = defaultMapper.decodeFromString(
        JsonObject.serializer(),
        callService(
            "backend",
            "GET",
            "http://localhost:8080/api/providers/browse?filterName=$providerId",
            accessToken,
            opts = listOf(
                "-H", "Project: $projectId"
            )
        ) ?: error("Provider creation failed. Check backend logs")
    )

    val credentials = runCatching {
        val p = ((providerObject["items"] as JsonArray)[0] as JsonObject)
        val publicKey = (p["publicKey"] as JsonPrimitive).content
        val refreshToken = (p["refreshToken"] as JsonPrimitive).content

        ProviderCredentials(publicKey, refreshToken, projectId)
    }.getOrNull() ?: error("Provider creation failed. Check backend logs.")

    return credentials
}

fun fetchAccessToken(): String {
    val tokenJson = callService("backend", "POST", "http://localhost:8080/auth/refresh", "theverysecretadmintoken")
        ?: error("Failed to contact UCloud/Core backend. Check to see if the backend service is running.")
    val accessToken = defaultMapper.decodeFromString(AccessTokenWrapper.serializer(), tokenJson).accessToken
    return accessToken
}

val defaultMapper = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun startCluster(compose: DockerCompose, noRecreate: Boolean) {
    LoadingIndicator("Starting virtual cluster...").use {
        compose.up(currentEnvironment, noRecreate = noRecreate).streamOutput().executeToText()
    }

    LoadingIndicator("Starting UCloud...").use {
        startService(serviceByName("backend")).executeToText()
    }

    LoadingIndicator("Waiting for UCloud to be ready...").use {
        val cmd = compose.exec(currentEnvironment, "backend", listOf("curl", "http://localhost:8080"), tty = false)
        cmd.allowFailure = true

        for (i in 0 until 100) {
            if (i > 20) cmd.streamOutput()
            if (cmd.executeToText().first != null) break
            Thread.sleep(1000)
        }
    }

    val allAddons = listAddons()
    for (provider in listConfiguredProviders()) {
        LoadingIndicator("Starting provider: ${ComposeService.providerFromName(provider).title}").use {
            startService(serviceByName(provider)).executeToText()
        }

        val addons = allAddons[provider]
        if (addons != null) {
            val p = ComposeService.providerFromName(provider)
            for (addon in addons) {
                LoadingIndicator("Starting addon: $addon").use {
                    p.startAddon(addon)
                }
            }
        }
    }
}

fun startService(
    service: Service,
): ExecutableCommand {
    if (service.useServiceConvention) {
        return compose.exec(
            currentEnvironment,
            service.containerName,
            listOf("/opt/ucloud/service.sh", "start"),
            tty = false
        ).streamOutput()
    } else {
        return compose.start(currentEnvironment, service.containerName).streamOutput()
    }
}
