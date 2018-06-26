package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.service.GuardedOutputStream
import dk.sdu.cloud.storage.services.CommandRunner
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.util.BoundaryContainedStream
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import kotlin.NoSuchElementException

class CephFSCommandRunnerFactory(
    private val userDao: CephFSUserDao,
    private val isDevelopment: Boolean
) : FSCommandRunnerFactory<StreamingCommandRunner>() {
    override fun invoke(user: String): StreamingCommandRunner {
        return StreamingCommandRunner(userDao, isDevelopment, user)
    }
}

@Suppress("unused")
data class ProcessRunnerAttributeKey<T>(val name: String)

class StreamingCommandRunner(
    private val cephFSUserDao: CephFSUserDao,
    private val isDevelopment: Boolean,
    override val user: String
) : CommandRunner {
    private val cache: MutableMap<ProcessRunnerAttributeKey<*>, Any> = hashMapOf()

    private val clientBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
    private val serverBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)

    private val interpreter: Process = run {
        val unixUser = cephFSUserDao.findUnixUser(user) ?: throw IllegalStateException("Could not find user")
        val prefix = if (isDevelopment) emptyList() else listOf("sudo", "-u", unixUser)
        val command = listOf(
            "ceph-interpreter",
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

    fun <T : Any> store(key: ProcessRunnerAttributeKey<T>, value: T) {
        cache[key] = value
    }

    fun <T : Any> retrieve(key: ProcessRunnerAttributeKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return (cache[key] ?: throw NoSuchElementException()) as T
    }

    fun <T : Any> retrieveOrNull(key: ProcessRunnerAttributeKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return cache[key] as T?
    }

    fun invalidate(key: ProcessRunnerAttributeKey<*>) {
        cache.remove(key)
    }

    fun <T> runCommand(
        command: InterpreterCommand,
        vararg args: String,
        writer: (OutputStream) -> Unit = {},
        consumer: (StreamingCommandRunner) -> T
    ): T {
        log.debug("Running command: $command ${args.joinToString(" ")}")

        if (!interpreter.isAlive) throw IllegalStateException("Unexpected EOF")

        val serializedCommand = StringBuilder().apply {
            append(command.command)
            append("\n")
            if (args.isNotEmpty()) {
                append(args.joinToString("\n"))
                append("\n")
            }
        }.toString().toByteArray()
        outputStream.write(serializedCommand)
        outputStream.flush()
        outputStream.use {
            writer(GuardedOutputStream(it))
        }

        return try {
            consumer(this)
        } finally {
            wrappedStdout.discardAndReset()

            if (log.isDebugEnabled) {
                wrappedStderr.bufferedReader().readText().lines().forEach { log.debug(it) }
            }
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
        private val log = LoggerFactory.getLogger(StreamingCommandRunner::class.java)
    }
}

fun InputStream.readLineUnbuffered(charset: Charset = Charsets.UTF_8): String {
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
    return String(lineBuilder.toByteArray(), charset)
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
    READ_OPEN("read-open"),
    STAT("stat"),
    MKDIR("make-dir"),
    DELETE("delete"),
    MOVE("move"),
    TREE("tree"),
    SYMLINK("symlink"),
    SETFACL("setfacl"),
    COPY("copy"),
    WRITE("write"),
    WRITE_OPEN("write-open"),
    GET_XATTR("get-xattr"),
    SET_XATTR("set-xattr"),
    LIST_XATTR("list-xattr"),
    DELETE_XATTR("delete-xattr"),
}

