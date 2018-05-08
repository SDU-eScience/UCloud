package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.service.GuardedOutputStream
import dk.sdu.cloud.storage.util.BashEscaper
import dk.sdu.cloud.storage.util.BoundaryContainedStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
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

    fun run(command: List<String>, directory: String? = null): IProcess

    fun runWithResultAsInMemoryString(
        command: List<String>,
        directory: String? = null
    ): InMemoryProcessResultAsString {
        val process = run(command, directory)

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val status = process.waitFor()
        log.debug { "status: $status" }
        log.debug { "stdout: $stdout" }
        log.debug { "stderr: $stderr" }

        return InMemoryProcessResultAsString(status, stdout, stderr)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProcessRunner::class.java)
    }
}

@Suppress("FunctionName")
fun SimpleCephFSProcessRunner(cloudToCephFsDao: CloudToCephFsDao, isDevelopment: Boolean): ProcessRunnerFactory {
    return { user: String ->
        CephFSProcessRunner(cloudToCephFsDao, isDevelopment, user)
    }
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

    override fun run(command: List<String>, directory: String?): IProcess {
        return ProcessBuilder().apply {
            val prefix = asUser(user)

            val bashCommand = if (directory == null) {
                command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
            } else {
                "cd ${BashEscaper.safeBashArgument(directory)} ; " +
                        command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
            }
            log.debug("Running command (user=$user): $bashCommand")

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

fun main(args: Array<String>) {
    val streamingProcessRunner = StreamingProcessRunner(CloudToCephFsDao(true), true, "jonas@hinchely.dk")
    println(streamingProcessRunner.runWithResultAsInMemoryString(listOf("echo", "Hello!", "World!")))
    println("Running last")
    println(streamingProcessRunner.runWithResultAsInMemoryString(listOf("sleep", "1")))
    println("Done")
    streamingProcessRunner.close()
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

    override fun run(command: List<String>, directory: String?): IProcess {
        val boundary = generateNewBoundary()

        val bashCommand = if (directory == null) {
            command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
        } else {
            "cd ${BashEscaper.safeBashArgument(directory)} ; " +
                    command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
        }

        log.debug("Running command (user=$user): $bashCommand")

        bashProcess.outputStream.apply {
            write((bashCommand + "\n").toByteArray())

            // Save status, mark end of process, write status
            write("STATUS=$?\n".toByteArray())
            write("echo $boundary\n".toByteArray())
            write("echo $boundary >&2\n".toByteArray())
            write("echo \$STATUS\n".toByteArray())

            // Always flush at end of command
            flush()
        }

        val boundaryBytes = (boundary + '\n').toByteArray()
        val wrappedStdout = BoundaryContainedStream(boundaryBytes, bashProcess.inputStream)
        val wrappedStderr = BoundaryContainedStream(boundaryBytes, bashProcess.errorStream)
        val outputStream = GuardedOutputStream(bashProcess.outputStream)

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
    }

}
