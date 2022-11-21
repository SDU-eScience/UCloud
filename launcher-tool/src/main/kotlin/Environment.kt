package dk.sdu.cloud

import com.jcraft.jsch.agentproxy.AgentProxyException
import net.schmizz.sshj.userauth.UserAuthException
import org.fusesource.jansi.Ansi.ansi
import java.io.File
import kotlin.math.absoluteValue
import kotlin.system.exitProcess

// NOTE(Dan): These are all directories which used to live in `.compose`. Don't allow anyone to pick from this list.
val blacklistedEnvNames = setOf(
    "postgres",
    "passwd",
    "home",
    "cluster-home",
    "im-config"
)

lateinit var localEnvironment: LocalFile
lateinit var currentEnvironment: LFile
var environmentIsRemote: Boolean = false
lateinit var portAllocator: PortAllocator
var composeName: String? = null

fun selectOrCreateEnvironment(baseDir: File): String {
    val alternativeEnvironments = (baseDir.listFiles() ?: emptyArray()).filter {
        it.isDirectory && it.name !in blacklistedEnvNames
    }
    if (alternativeEnvironments.isNotEmpty()) {
        val menu = object : Menu("Select an environment") {
            init {
                for (env in alternativeEnvironments) {
                    item(env.absolutePath, env.name)
                }
            }

            val createNew = item("new", "Create new environment")
        }

        val selected = menu.display(prompt)
        if (selected != menu.createNew) {
            return selected.name.substringAfterLast('/')
        }
    }

    val localOrRemoteMenu = object : Menu("Should this be a local or remote environment?") {
        val local = item("local", "Local environment on this machine")
        val remote = item("remote", "Remote environment on some other machine (via SSH)")
    }

    val newEnvironment = queryText(prompt, "Select a name for your environment", "default")
    if (newEnvironment in blacklistedEnvNames) {
        println("Illegal name. Try a different one.")
        exitProcess(1)
    }

    when (localOrRemoteMenu.display(prompt)) {
        localOrRemoteMenu.local -> {
            val env = LocalFile(
                File(
                    File(repoRoot.absolutePath, ".compose"),
                    newEnvironment
                ).also { it.mkdirs() }.absolutePath
            )
            currentEnvironment = env
            environmentIsRemote = File(env.jvmFile, "remote").exists()

            return newEnvironment.substringAfterLast('/')
        }

        localOrRemoteMenu.remote -> {
            val hostname = queryText(prompt, "Remote machine hostname")
            val username = queryText(prompt, "Remote username")

           val env = LocalFile(
                File(
                    File(repoRoot.absolutePath, ".compose"),
                    newEnvironment
                ).also { it.mkdirs() }.absolutePath
            )

            currentEnvironment = env
            environmentIsRemote = true

            env.child("remote").writeText("$username@$hostname")
            return newEnvironment.substringAfterLast('/')
        }

        else -> error("unreachable")
    }
}

data class InitEnvironmentResult(val shouldStartEnvironment: Boolean)

fun initCurrentEnvironment(): InitEnvironmentResult {
    val baseDir = File(repoRoot.jvmFile, ".compose").also { it.mkdirs() }
    val currentEnvironmentName = runCatching { File(baseDir, "current.txt").readText() }.getOrNull()
    val currentIsRemote = if (currentEnvironmentName != null) {
        runCatching { File(File(baseDir, currentEnvironmentName), "remote").exists() }.getOrElse { false }
    } else {
        false
    }

    val env = if (currentEnvironmentName == null) {
        null
    } else {
        runCatching { File(baseDir, currentEnvironmentName).takeIf { it.exists() } }.getOrNull()
    }

    if (env == null) {
        println("No active environment detected!")
        selectOrCreateEnvironment(baseDir)
    } else {
        println(ansi().render("Active environment: ").bold().render(env.name).boldOff())
        println()

        currentEnvironment = LocalFile(env.absolutePath)
        environmentIsRemote = currentIsRemote
    }

    initIO()

    File(baseDir, "current.txt").writeText(currentEnvironment.name)
    return InitEnvironmentResult(shouldStartEnvironment = currentEnvironmentName == null)
}

fun initIO() {
    if (environmentIsRemote) {
        val baseDir = currentEnvironment.absolutePath
        val lineSplit = File(baseDir, "remote").readText().trim().split("@")
        if (lineSplit.size != 2) {
            error("Unable to parse remote details from environment: $baseDir. Try deleting this folder.")
        }

        val conn = try {
            SshConnection.create(username = lineSplit[0], host = lineSplit[1])
        } catch (ex: UserAuthException) {
            println("Unable to connect to ${lineSplit[0]}@${lineSplit[1]}!")
            println("Please make sure that you have ssh-agent running and are able to connect to this server.")
            println("Try running ssh-add and retry this command.")
            exitProcess(1)
        } catch (ex: AgentProxyException) {
            if (ex.message?.contains("does not support -U") == true) {
                println("This tool requires nc to be installed with -U support for remote development. If you are")
                println("on macOS try the following: brew install nmap. If you are on Linux, try installing netcat")
                println("from your package manager.")
                exitProcess(1)
            } else {
                throw ex
            }
        }
        localEnvironment = currentEnvironment as LocalFile

        commandFactory = RemoteExecutableCommandFactory(conn)
        fileFactory = RemoteFileFactory(conn)

        val remoteRepoRoot = fileFactory.create("ucloud")
        if (!remoteRepoRoot.exists()) syncRepository()
        val remoteEnvironment = remoteRepoRoot.child(".compose/${currentEnvironment.name}").also {
            it.mkdirs()
        }

        portAllocator = PortAllocator.Remapped((conn.remoteRoot.hashCode() % 20_000) + 20_000)
        composeName = currentEnvironment.name + "_" + conn.remoteRoot.hashCode().absoluteValue
        currentEnvironment = remoteEnvironment
    } else {
        commandFactory = LocalExecutableCommandFactory()
        fileFactory = LocalFileFactory()

        localEnvironment = currentEnvironment as LocalFile
        portAllocator = PortAllocator.Direct
    }
}

fun listConfiguredProviders(): List<String> {
    return runCatching { File(localEnvironment.jvmFile, "providers.txt").readLines() }.getOrNull() ?: emptyList()
}

fun addProvider(providerId: String) {
    File(localEnvironment.jvmFile, "providers.txt").appendText(providerId + "\n")
}
