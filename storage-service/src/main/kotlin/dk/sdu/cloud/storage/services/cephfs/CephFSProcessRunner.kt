package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.service.GuardedOutputStream
import dk.sdu.cloud.storage.util.BashEscaper
import dk.sdu.cloud.storage.util.BoundaryContainedStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private inline fun Logger.debug(closure: () -> String) {
    if (isDebugEnabled) debug(closure())
}

data class InMemoryProcessResultAsString(val status: Int, val stdout: String, val stderr: String)

interface IProcess : Closeable {
    val inputStream: InputStream
    val errorStream: InputStream
    val outputStream: OutputStream
    fun waitFor(): Int
}

class JavaProcess(private val realProcess: Process) : IProcess {
    override val inputStream: InputStream
        get() = realProcess.inputStream
    override val errorStream: InputStream
        get() = realProcess.errorStream
    override val outputStream: OutputStream
        get() = realProcess.outputStream

    override fun waitFor(): Int {
        return realProcess.waitFor()
    }

    override fun close() {
        realProcess.destroy()
    }
}

typealias ProcessRunnerFactory = (user: String) -> ProcessRunner

interface ProcessRunner : Closeable {
    val user: String

    fun run(command: List<String>, directory: String? = null, noEscape: Boolean = false): IProcess

    fun runWithResultAsInMemoryString(
        command: List<String>,
        directory: String? = null
    ): InMemoryProcessResultAsString {
        val process = run(command, directory)

        process.outputStream.close()
        val stdout = process.inputStream.bufferedReader().readText()
        log.debug { "stdout: $stdout" }
        val stderr = process.errorStream.bufferedReader().readText()
        log.debug { "stderr: $stderr" }
        val status = process.waitFor()
        log.debug { "status: $status" }

        return InMemoryProcessResultAsString(status, stdout, stderr)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProcessRunner::class.java)
    }
}

@Suppress("FunctionName")
fun SimpleCephFSProcessRunnerFactory(cloudToCephFsDao: CloudToCephFsDao, isDevelopment: Boolean): ProcessRunnerFactory {
    return { user: String ->
        CephFSProcessRunner(cloudToCephFsDao, isDevelopment, user)
    }
}

@Suppress("FunctionName")
fun StreamingProcessRunnerFactory(cloudToCephFsDao: CloudToCephFsDao, isDevelopment: Boolean): ProcessRunnerFactory {
    return { StreamingProcessRunner(cloudToCephFsDao, isDevelopment, it) }
}

class CephFSProcessRunner(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean,
    override val user: String
) : ProcessRunner {
    private fun asUser(cloudUser: String): List<String> {
        val user = cloudToCephFsDao.findUnixUser(cloudUser) ?: throw IllegalStateException("Could not find user")
        return if (!isDevelopment) listOf("sudo", "-u", user) else emptyList()
    }

    override fun run(command: List<String>, directory: String?, noEscape: Boolean): IProcess {
        return ProcessBuilder().apply {
            val prefix = asUser(user)

            val bashCommand = if (directory == null) {
                command.joinToString(" ") {
                    if (noEscape) it
                    else BashEscaper.safeBashArgument(it)
                }
            } else {
                "cd ${BashEscaper.safeBashArgument(directory)} ; " +
                        command.joinToString(" ") {
                            if (noEscape) it
                            else BashEscaper.safeBashArgument(it)
                        }
            }
            log.debug("Running command (user=$user): $bashCommand [$command]")

            val wrappedCommand = listOf("bash", "-c", bashCommand)
            command(prefix + wrappedCommand)
        }.start().let { JavaProcess(it) }
    }

    override fun close() {
        // No-op
    }

    companion object {
        private val log =
            LoggerFactory.getLogger(CephFSProcessRunner::class.java)
    }
}

class StreamingProcessRunner(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean,
    override val user: String
) : ProcessRunner {
    private val bashProcess = run {
        val unixUser = cloudToCephFsDao.findUnixUser(user) ?: throw IllegalStateException("Could not find user")
        val command = if (isDevelopment) listOf("bash") else listOf("sudo", "-u", unixUser, "bash")

        ProcessBuilder().apply { command(command) }.start()
    }

    private fun generateNewBoundary(): String {
        return "process-runner-boundary-${UUID.randomUUID()}"
    }

    fun debug() {
        bashProcess.outputStream.close()
        bashProcess.waitFor()
        println(bashProcess.inputStream.bufferedReader().readText())
        println(bashProcess.errorStream.bufferedReader().readText())
    }

    override fun run(command: List<String>, directory: String?, noEscape: Boolean): IProcess {
        val boundary = generateNewBoundary()

        val bashCommand = if (directory == null) {
            command.joinToString(" ") {
                if (noEscape) it
                else BashEscaper.safeBashArgument(it)
            }
        } else {
            "cd ${BashEscaper.safeBashArgument(directory)} ; " +
                    command.joinToString(" ") {
                        if (noEscape) it
                        else BashEscaper.safeBashArgument(it)
                    }
        }

        log.debug("Running command (user=$user): $bashCommand")

        log.debug("Bash process alive? ${bashProcess.isAlive}")
        val out = bashProcess.outputStream
        out.apply {
            fun w(str: String) {
                log.debug(str)
                write(str.toByteArray())
            }

            w(("$bashCommand;"))

            // Save status, mark end of process, write status
            // NOTE(Dan): Do not use new-lines, as these will leak into the output stream of sub-processes
            w("STATUS=$?;")
            w("echo $boundary;")
            w("echo $boundary >&2;")
            w("echo \$STATUS\n")

            // Always flush at end of command
            flush()
        }

        val boundaryBytes = (boundary + '\n').toByteArray()
        val wrappedStdout = BoundaryContainedStream(boundaryBytes, bashProcess.inputStream)
        val wrappedStderr = BoundaryContainedStream(boundaryBytes, bashProcess.errorStream)
        val outputStream = GuardedOutputStream(out)

        return BoundaryContainedProcess(
            wrappedStdout,
            wrappedStderr,
            outputStream,
            bashProcess.inputStream,
            boundaryBytes
        )
    }

    override fun close() {
        bashProcess.outputStream.close()
        bashProcess.destroy()
    }

    companion object {
        private val log = LoggerFactory.getLogger(StreamingProcessRunner::class.java)
    }
}

class BoundaryContainedProcess(
    override val inputStream: BoundaryContainedStream,
    override val errorStream: BoundaryContainedStream,
    override val outputStream: OutputStream,

    private val originalStream: InputStream,
    private val boundaryBytes: ByteArray
) : IProcess {
    override fun waitFor(): Int {
        inputStream.discardAll()
        errorStream.discardAll()
        outputStream.close()

        val remaining = inputStream.readRemainingAfterBoundary()

        val buffer = ByteArray(256)
        System.arraycopy(remaining, 0, buffer, 0, remaining.size)
        var ptr = remaining.size

        if (ptr > boundaryBytes.size) {
            for (i in boundaryBytes.size until ptr) {
                if (buffer[i] == '\n'.toByte()) {
                    return String(buffer, boundaryBytes.size, i - boundaryBytes.size).toInt()
                }
            }
        }

        while (true) {
            val read = originalStream.read()
            if (ptr < boundaryBytes.size) {
                buffer[ptr++] = read.toByte()
            } else {
                when (read) {
                    -1 -> throw IllegalStateException("Unexpected end of stream. Boundary not found")
                    '\n'.toInt() -> return String(buffer, boundaryBytes.size, ptr - boundaryBytes.size).toInt()
                    else -> buffer[ptr++] = read.toByte()
                }
            }
        }
    }

    override fun close() {
        inputStream.discardAll()
        errorStream.discardAll()
        outputStream.close()
    }
}
