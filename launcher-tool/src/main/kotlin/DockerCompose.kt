package dk.sdu.cloud

fun findDocker(): String {
    return ExecutableCommand(listOf("/usr/bin/which", "docker")).executeToText().first?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Could not find docker")
}

fun findCompose(): DockerCompose {
    val dockerExe = findDocker()

    val hasPluginStyle = runCatching {
        ExecutableCommand(listOf(dockerExe, "compose", "version"), allowFailure = true).executeToText().first
    }.getOrNull()?.takeIf { it.startsWith("Docker Compose", ignoreCase = true) } != null

    if (hasPluginStyle) return DockerCompose.Plugin(dockerExe)

    val dockerComposeExe = ExecutableCommand(
        listOf("/usr/bin/which", "docker-compose"),
        allowFailure = true
    ).executeToText().first?.trim()?.takeIf { it.isNotEmpty() }

    val hasClassicStyle = dockerComposeExe != null
    if (hasClassicStyle) return DockerCompose.Classic(dockerComposeExe!!)

    error("Could not find docker compose!")
}

sealed class DockerCompose {
    abstract fun up(directory: LFile, noRecreate: Boolean = false): ExecutableCommand
    abstract fun down(directory: LFile, deleteVolumes: Boolean = false): ExecutableCommand
    abstract fun ps(directory: LFile): ExecutableCommand
    abstract fun logs(directory: LFile, container: String): ExecutableCommand
    abstract fun start(directory: LFile, container: String): ExecutableCommand
    abstract fun stop(directory: LFile, container: String): ExecutableCommand
    abstract fun exec(directory: LFile, container: String, command: List<String>, tty: Boolean = true): ExecutableCommand

    class Classic(val exe: String) : DockerCompose() {
        private fun base(directory: LFile): List<String> =
            buildList {
                add(exe)
                add("--project-directory")
                add(directory.absolutePath)
                val composeName = composeName
                if (composeName != null) {
                    add("-p")
                    add(composeName)
                }
            }

        override fun up(directory: LFile, noRecreate: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    addAll(base(directory))
                    addAll(listOf("up", "-d") )
                    if (noRecreate) add("--no-recreate")
                },
                directory
            )

        override fun down(directory: LFile, deleteVolumes: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    addAll(base(directory))
                    add("down")
                    if (deleteVolumes) add("-v")
                },
                directory
            )

        override fun ps(directory: LFile): ExecutableCommand =
            ExecutableCommand(
                base(directory) + listOf("ps"),
                directory,
                allowFailure = true
            )

        override fun logs(directory: LFile, container: String): ExecutableCommand =
            ExecutableCommand(
                base(directory) +
                listOf(
                    "logs",
                    "--follow",
                    "--no-log-prefix",
                    container
                ), directory
            )

        override fun exec(directory: LFile, container: String, command: List<String>, tty: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    addAll(base(directory))
                    add("exec")
                    if (!tty) add("-T")
                    add(container)
                    addAll(command)
                },
                directory
            )

        override fun start(directory: LFile, container: String): ExecutableCommand =
            ExecutableCommand(base(directory) + listOf("start", container), directory)

        override fun stop(directory: LFile, container: String): ExecutableCommand =
            ExecutableCommand(base(directory) + listOf("stop", container), directory)
    }

    class Plugin(val exe: String) : DockerCompose() {
        private fun base(directory: LFile): List<String> =
            buildList {
                add(exe)
                add("compose")
                add("--project-directory")
                add(directory.absolutePath)
                val composeName = composeName
                if (composeName != null) {
                    add("-p")
                    add(composeName)
                }
            }

        override fun up(directory: LFile, noRecreate: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    addAll(base(directory))
                    addAll(listOf("up", "-d") )
                    if (noRecreate) add("--no-recreate")
                },
                directory
            )

        override fun down(directory: LFile, deleteVolumes: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    addAll(base(directory))

                    if (!deleteVolumes) {
                        add("down")
                    } else {
                        add("rm")
                        add("--stop")
                        add("--volumes")
                        add("--force")
                    }
                },
                directory
            )

        override fun ps(directory: LFile): ExecutableCommand =
            ExecutableCommand(
                base(directory) + listOf("ps"),
                directory,
                allowFailure = true
            )

        override fun logs(directory: LFile, container: String): ExecutableCommand =
            ExecutableCommand(
                base(directory) + listOf(
                    "logs",
                    "--follow",
                    "--no-log-prefix",
                    container
                ), directory
            )

        override fun exec(directory: LFile, container: String, command: List<String>, tty: Boolean): ExecutableCommand =
            ExecutableCommand(
                buildList {
                    addAll(base(directory))
                    add("exec")
                    if (!tty) add("-T")
                    add(container)
                    addAll(command)
                },
                directory
            )

        override fun start(directory: LFile, container: String): ExecutableCommand =
            ExecutableCommand(
                base(directory) + listOf("start", container),
                directory
            )

        override fun stop(directory: LFile, container: String): ExecutableCommand =
            ExecutableCommand(
                base(directory) + listOf("stop", container),
                directory
            )
    }
}
