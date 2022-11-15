package dk.sdu.cloud

import java.io.File
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

fun findDocker(): String {
    return startProcessAndCollectToString(listOf("/usr/bin/which", "docker")).stdout.trim().takeIf { it.isNotEmpty() }
        ?: error("Could not find Docker!")
}

fun findCompose(): DockerCompose {
    val dockerExe = findDocker()

    val hasPluginStyle = runCatching {
        startProcessAndCollectToString(listOf(dockerExe, "compose", "version"))
    }.getOrNull()?.takeIf { it.stdout.startsWith("Docker Compose", ignoreCase = true) } != null

    if (hasPluginStyle) return DockerCompose.Plugin(dockerExe)

    val dockerComposeExe = startProcessAndCollectToString(listOf("/usr/bin/which", "docker-compose")).stdout.trim()
        .takeIf { it.isNotEmpty() }

    val hasClassicStyle = dockerComposeExe != null
    if (hasClassicStyle) return DockerCompose.Classic(dockerComposeExe!!)

    error("Could not find docker compose!")
}

val debugCommands = System.getenv("DEBUG_COMMANDS") != null

data class ExecutableCommand(
    val args: List<String>,
    val workingDir: File? = null,
    val postProcessor: (result: ProcessResultText) -> String = { it.stdout },
    var allowFailure: Boolean = false,
    var deadlineInMillis: Long = 1000 * 60 * 5,
) {
    fun toBashScript(): String {
        return buildString {
            if (workingDir != null) appendLine("cd '$workingDir'")
            appendLine(args.joinToString(" ") { "'$it'" })
        }
    }

    fun executeToText(): Pair<String?, String> {
        if (debugCommands) println("Command: " + args.joinToString(" ") { "'$it'" })

        val result = startProcessAndCollectToString(args, workingDir = workingDir, deadlineInMillis = deadlineInMillis)

        if (debugCommands) {
            println("  Exit code: ${result.statusCode}")
            println("  Stdout: ${result.stdout}")
            println("  Stderr: ${result.stderr}")
        }

        if (result.statusCode != 0) {
            if (allowFailure) return Pair(null, result.stdout + result.stderr)

            println("Command failed!")
            println("Command: " + args.joinToString(" ") { "'$it'" })
            println("Directory: $workingDir")
            println("Exit code: ${result.statusCode}")
            println("Stdout: ${result.stdout}")
            println("Stderr: ${result.stderr}")
            exitProcess(result.statusCode)
        }
        return Pair(postProcessor(result), "")
    }
}

sealed class DockerCompose {
    abstract fun up(directory: File): ExecutableCommand
    abstract fun down(directory: File, deleteVolumes: Boolean = false): ExecutableCommand
    abstract fun ps(directory: File): ExecutableCommand
    abstract fun logs(directory: File, container: String): ExecutableCommand
    abstract fun start(directory: File, container: String): ExecutableCommand
    abstract fun stop(directory: File, container: String): ExecutableCommand
    abstract fun exec(directory: File, container: String, command: List<String>, tty: Boolean = true): ExecutableCommand

    class Classic(val exe: String) : DockerCompose() {
        override fun up(directory: File): ExecutableCommand =
            ExecutableCommand(listOf(exe, "--project-directory", directory.absolutePath, "up", "-d"), directory)

        override fun down(directory: File, deleteVolumes: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    add(exe)
                    add("--project-directory")
                    add(directory.absolutePath)
                    add("down")
                    if (deleteVolumes) add("-v")
                },
                directory
            )

        override fun ps(directory: File): ExecutableCommand =
            ExecutableCommand(
                listOf(exe, "--project-directory", directory.absolutePath, "ps"),
                directory,
                allowFailure = true
            )

        override fun logs(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(
                listOf(
                    exe,
                    "--project-directory",
                    directory.absolutePath,
                    "logs",
                    "--follow",
                    "--no-log-prefix",
                    container
                ), directory
            )

        override fun exec(directory: File, container: String, command: List<String>, tty: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    add(exe)
                    add("--project-directory")
                    add(directory.absolutePath)
                    add("exec")
                    if (!tty) add("-T")
                    add(container)
                    addAll(command)
                },
                directory
            )

        override fun start(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(listOf(exe, "--project-directory", directory.absolutePath, "start", container), directory)

        override fun stop(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(listOf(exe, "--project-directory", directory.absolutePath, "stop", container), directory)
    }

    class Plugin(val exe: String) : DockerCompose() {
        override fun up(directory: File): ExecutableCommand =
            ExecutableCommand(
                listOf(exe, "compose", "--project-directory", directory.absolutePath, "up", "-d"),
                directory
            )

        override fun down(directory: File, deleteVolumes: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    add(exe)
                    add("compose")
                    add("--project-directory")
                    add(directory.absolutePath)
                    add("down")
                    if (deleteVolumes) add("-v")
                },
                directory
            )

        override fun ps(directory: File): ExecutableCommand =
            ExecutableCommand(
                listOf(exe, "compose", "--project-directory", directory.absolutePath, "ps"),
                directory,
                allowFailure = true
            )

        override fun logs(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(
                listOf(
                    exe,
                    "compose",
                    "--project-directory",
                    directory.absolutePath,
                    "logs",
                    "--follow",
                    "--no-log-prefix",
                    container
                ), directory
            )

        override fun exec(directory: File, container: String, command: List<String>, tty: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    add(exe)
                    add("compose")
                    add("--project-directory")
                    add(directory.absolutePath)
                    add("exec")
                    if (!tty) add("-T")
                    add(container)
                    addAll(command)
                },
                directory
            )

        override fun start(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(
                listOf(exe, "compose", "--project-directory", directory.absolutePath, "start", container),
                directory
            )

        override fun stop(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(
                listOf(exe, "compose", "--project-directory", directory.absolutePath, "stop", container),
                directory
            )
    }
}
