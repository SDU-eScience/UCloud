package dk.sdu.cloud

import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector
import com.jcraft.jsch.agentproxy.sshj.AuthAgent
import com.jcraft.jsch.agentproxy.usocket.NCUSocketFactory
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import java.lang.StringBuilder
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

var disableRemoteFileWriting = false

class SshConnection(
    val username: String,
    val host: String,
    val ssh: SSHClient,
    val remoteRoot: String,
) {
    val sftp = ssh.newSFTPClient()
    val shell = ssh.startSession().startShell()
    val shellOutput = shell.inputStream.bufferedReader()
    val shellError = shell.errorStream.bufferedReader()

    inline fun <R> useSession(block: (Session) -> R): R {
        return ssh.startSession().use(block)
    }

    fun exec(args: List<String>): Session.Command {
        return ssh.startSession().use { session ->
            session.exec(
                args.joinToString(" ") {
                    require(!it.contains("'")) { "Command cannot contain: '" }

                    "'$it'"
                }
            )
        }
    }

    fun close() {
        shell.close()
        sftp.close()
        ssh.close()
    }

    companion object {
        fun create(username: String, host: String): SshConnection {
            LoadingIndicator("Connecting to $username@$host").use {
                val connector = SSHAgentConnector(NCUSocketFactory())
                val agent = AgentProxy(connector)
                val authMethods = agent.identities.filterNotNull().map { AuthAgent(agent, it) }

                val ssh = SSHClient()
                ssh.loadKnownHosts()
                ssh.connect(host)
                ssh.auth(username, authMethods)

                val remoteRoot = ssh.startSession().use { session ->
                    session.exec("pwd").inputStream.readAllBytes().decodeToString().trim()
                }

                return SshConnection(username, host, ssh, remoteRoot)
            }
        }
    }
}

fun syncRepository(conn: SshConnection) {
    LoadingIndicator("Synchronizing repository with remote").use {
        ProcessBuilder(
            listOf(
                "rsync",
                "-zvhPr",
                "--exclude=/.git",
                "--exclude=/.compose",
                "--filter=:- .gitignore",
                "--delete",
                ".",
                "${conn.username}@${conn.host}:ucloud"
            )
        ).apply {
            redirectOutput(ProcessBuilder.Redirect.DISCARD)
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
    }
}

class RemoteFileFactory(private val connection: SshConnection) : FileFactory() {
    override fun create(path: String): LFile = RemoteFile(connection, path)
}

class RemoteFile(private val connection: SshConnection, path: String) : LFile(path) {
    init {
        require(!path.contains("'")) { "Paths cannot contain: '" }
    }

    override val absolutePath: String =
        if (!path.startsWith("/")) connection.remoteRoot + "/" + path.removePrefix("/")
        else path

    override fun exists(): Boolean {
        return connection.useSession { it.exec("stat '$path'").also { it.join() }.exitStatus == 0 }
    }

    override fun child(subpath: String): LFile {
        return RemoteFile(connection, absolutePath.removeSuffix("/") + "/" + subpath)
    }

    override fun writeText(text: String) {
        if (disableRemoteFileWriting) return
        val sftp = connection.sftp
        val localFile = Files.createTempFile("temp", "temp").toFile().also { it.writeText(text) }
        sftp.fileTransfer.upload(localFile.absolutePath, absolutePath)
    }

    override fun writeBytes(bytes: ByteArray) {
        if (disableRemoteFileWriting) return
        val sftp = connection.sftp
        val localFile = Files.createTempFile("temp", "temp").toFile().also { it.writeBytes(bytes) }
        sftp.fileTransfer.upload(localFile.absolutePath, absolutePath)
    }

    override fun appendText(text: String) {
        if (disableRemoteFileWriting) return
        connection.useSession {
            it.exec(
                """
                    cat >> '$absolutePath' << EOF
                    $text
                    EOF
                """.trimIndent()
            ).join()
        }
    }

    override fun delete() {
        if (disableRemoteFileWriting) return
        connection.useSession { it.exec("rm -rf '$absolutePath'").join() }
    }

    override fun mkdirs() {
        if (disableRemoteFileWriting) return
        connection.sftp.mkdirs(absolutePath)
    }
}

class RemoteExecutableCommandFactory(val connection: SshConnection) : ExecutableCommandFactory() {
    override fun create(
        args: List<String>,
        workingDir: LFile?,
        postProcessor: (result: ProcessResultText) -> String,
        allowFailure: Boolean,
        deadlineInMillis: Long
    ): ExecutableCommand = RemoteExecutableCommand(
        connection,
        args,
        workingDir,
        postProcessor,
        allowFailure,
        deadlineInMillis
    )
}

class RemoteExecutableCommand(
    private val connection: SshConnection,
    override val args: List<String>,
    override val workingDir: LFile?,
    override val postProcessor: (result: ProcessResultText) -> String,
    override var allowFailure: Boolean,
    override var deadlineInMillis: Long
) : ExecutableCommand {
    override fun toBashScript(): String {
        return buildString {
            append("ssh -t ")
            append(connection.username)
            append('@')
            append(connection.host)
            append(' ')
            append('"')
            if (workingDir != null) append("cd '$workingDir'; ")
            append(args.joinToString(" ") { "'$it'" })
            appendLine('"')
        }
    }

    override fun executeToText(): Pair<String?, String> {
        val boundary = UUID.randomUUID().toString()
        connection.shell.outputStream.write(
            buildString {
                if (workingDir != null) appendLine("cd '$workingDir';")
                append(args.joinToString(" ") { "'$it'" })
                appendLine(" < /dev/null")
                appendLine("st=${'$'}?")
                appendLine("echo")
                appendLine("echo $boundary-${"$"}st")
                appendLine("echo 1>&2 $boundary")
            }.also {println(it)}.encodeToByteArray()
        )

        connection.shell.outputStream.flush()

        val outputBuilder = StringBuilder()
        val errBuilder = StringBuilder()
        var exitCode = 0
        val stdoutThread = Thread {
            while (true) {
                println("waiting for line")
                val line = connection.shellOutput.readLine()
                println("got line: $line")
                if (line.startsWith(boundary)) {
                    exitCode = line.removePrefix("$boundary-").trim().toInt()
                    break
                } else {
                    outputBuilder.appendLine(line)
                }
            }
        }.also { it.start() }

        val stderrThread = Thread {
            while (true) {
                val line = connection.shellError.readLine()
                println("err line: $line")
                if (line.startsWith(boundary)) {
                    break
                } else {
                    errBuilder.appendLine(line)
                }
            }
        }.also { it.start() }

        stdoutThread.join()
        stderrThread.join()

        val output = outputBuilder.toString()
        val err = errBuilder.toString()

        if (exitCode != 0) {
            if (allowFailure) {
                repeat(10) { println() }
                println("Command failed!")
                println("Command: " + args.joinToString(" ") { "'$it'" })
                println("Directory: $workingDir")
                println("Exit code: ${exitCode}")
                println("Stdout: ${output}")
                println("Stderr: ${err}")
                repeat(10) { println() }
                return Pair(null, output + err)
            } else {
                println("Command failed!")
                println("Command: " + args.joinToString(" ") { "'$it'" })
                println("Directory: $workingDir")
                println("Exit code: ${exitCode}")
                println("Stdout: ${output}")
                println("Stderr: ${err}")
                exitProcess(exitCode)
            }
        }
        return Pair(output, "")

        /*
        if (exitCode != 0) {

        }

        connection.shellOutput.readLine()
        connection.useSession { session ->
            val command = session.exec(buildString {
                append("sh -c \"")
                if (workingDir != null) append("cd '$workingDir'; ")
                append(args.joinToString(" ") { "'$it'" })
                append("\"")
            })

            runCatching {
                command.join(deadlineInMillis, TimeUnit.MILLISECONDS)
            }

            val outs = command.inputStream.readAllBytes().decodeToString()
            val errs = command.errorStream.readAllBytes().decodeToString()

            if (command.exitStatus != 0) {
                if (allowFailure) {
                    repeat(10) { println() }
                    println("Command failed!")
                    println("Command: " + args.joinToString(" ") { "'$it'" })
                    println("Directory: $workingDir")
                    println("Exit code: ${command.exitStatus}")
                    println("Stdout: ${outs}")
                    println("Stderr: ${errs}")
                    repeat(10) { println() }
                    return Pair(null, outs + errs)
                } else {
                    println("Command failed!")
                    println("Command: " + args.joinToString(" ") { "'$it'" })
                    println("Directory: $workingDir")
                    println("Exit code: ${command.exitStatus}")
                    println("Stdout: ${outs}")
                    println("Stderr: ${errs}")
                    exitProcess(command.exitStatus)
                }
            }
            return Pair(postProcessor(ProcessResultText(command.exitStatus, outs, errs)), "")

        }
         */
    }
}
