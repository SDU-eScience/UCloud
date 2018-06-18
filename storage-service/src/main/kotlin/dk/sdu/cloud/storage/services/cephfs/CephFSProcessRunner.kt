package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.service.GuardedOutputStream
import dk.sdu.cloud.storage.services.FSException
import dk.sdu.cloud.storage.util.BashEscaper
import dk.sdu.cloud.storage.util.BoundaryContainedStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.math.absoluteValue

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

typealias ProcessRunnerFactory = (user: String) -> StreamingProcessRunner

interface ProcessRunner : Closeable {
    val user: String

    @Deprecated("deprecated")
    fun run(command: List<String>, directory: String? = null, noEscape: Boolean = false): IProcess

    @Deprecated("deprecated")
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
        StreamingProcessRunner(cloudToCephFsDao, isDevelopment, user)
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

    override fun run(command: List<String>, directory: String?, noEscape: Boolean): IProcess {
        return ProcessBuilder().apply {
            val prefix = asUser(user)

            val bashCommand = if (directory == null) {
                command.joinToString(" ") {
                    if (noEscape) it
                    else BashEscaper.safeBashArgument(it)
                }
            } else {
                // TODO We need to ensure directory exists. We cannot do this with File(directory)
                "cd ${BashEscaper.safeBashArgument(directory)} && " +
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
    user: String
) : ProcessRunner by CephFSProcessRunner(cloudToCephFsDao, isDevelopment, user) {
    private val clientBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
    private val serverBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)

    private val interpreter: Process = run {
        val unixUser = cloudToCephFsDao.findUnixUser(user) ?: throw IllegalStateException("Could not find user")
        val prefix = if (isDevelopment) emptyList() else listOf("sudo", "-u", unixUser)
        val command = listOf(
            "/Users/dthrane/work/sdu-cloud/storage-service/native/cmake-build-release/ceph-interpreter",
            String(clientBoundary, Charsets.UTF_8),
            String(serverBoundary, Charsets.UTF_8)
        )

        ProcessBuilder().apply { command(prefix + command) }.start()
    }

    private val wrappedStdout =
        BoundaryContainedStream(serverBoundary, interpreter.inputStream)

    private val wrappedStderr =
        BoundaryContainedStream(serverBoundary, interpreter.errorStream)

    val stdout: InputStream = wrappedStdout
    val stderr: InputStream = wrappedStdout

    fun stdoutLineSequence(): Sequence<String> = stdout.bufferedReader().lineSequence()

    private val outputStream =
        StreamingOutputStream(interpreter.outputStream, onClose = {
            it.write(clientBoundary)
            it.flush()
        })

    fun <T> runCommand(
        command: InterpreterCommand,
        vararg args: String,
        writer: (OutputStream) -> Unit = {},
        consumer: (StreamingProcessRunner) -> T
    ): T {
        log.debug("Running command: $command ${args.joinToString(" ")}")
        log.debug("Interpreter alive? ${interpreter.isAlive}")

        if (!interpreter.isAlive) throw IllegalStateException("Unexpected EOF")

        outputStream.write((command.command + " " + args.joinToString("\n") + "\n").toByteArray())
        outputStream.flush()
        outputStream.use {
            writer(GuardedOutputStream(it))
        }

        return consumer(this).also {
            wrappedStdout.discardAndReset()
            wrappedStderr.discardAndReset()
        }
    }

    fun clearBytes(numberOfBytes: Long) {
        log.debug("Clearing $numberOfBytes from stdout")
        wrappedStdout.manualClearNextBytes(numberOfBytes)
    }

    override fun close() {
        interpreter.outputStream.close()
        interpreter.destroy()
    }

    companion object {
        private val log = LoggerFactory.getLogger(StreamingProcessRunner::class.java)
    }
}

fun InputStream.simpleReadLine(): String {
    val lineBuilder = ArrayList<Byte>()
    var next = read()
    while (next != -1) {
        if (next == '\n'.toInt()) {
            break
        } else {
            lineBuilder.add(next.toByte())
        }

        next = read()
    }
    return String(lineBuilder.toByteArray())
}

class StreamingOutputStream(private val delegate: OutputStream, private val onClose: (OutputStream) -> Unit) :
    OutputStream() {
    override fun write(b: Int) {
        delegate.write(b)
    }

    override fun write(b: ByteArray?) {
        delegate.write(b)
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        delegate.write(b, off, len)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        onClose(this)
    }
}

enum class InterpreterCommand(val command: String) {
    LIST_DIRECTORY("list-directory"),
    READ("read"),
    STAT("stat"),
    MKDIR("make-dir"),
    DELETE("delete"),
    MOVE("move"),
    WRITE("write"),
    WRITE_OPEN("write-open"),

    GET_XATTR("get-xattr"),
    SET_XATTR("set-xattr"),
    LIST_XATTR("list-xattr"),
    DELETE_XATTR("delete-xattr"),

    TREE("tree")
}

fun throwExceptionBasedOnStatus(status: Int): Nothing {
    when (status.absoluteValue) {
        1, 20, 21, 22 -> throw FSException.BadRequest()
        2, 93 -> throw FSException.NotFound()
        5, 6, 16, 19, 23, 24, 27, 28, 30, 31 -> throw FSException.IOException()
        13 -> throw FSException.PermissionException()
        17 -> throw FSException.AlreadyExists()

        else -> throw FSException.CriticalException("Unknown status code $status")
    }
}
