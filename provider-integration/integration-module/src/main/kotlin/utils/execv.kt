package dk.sdu.cloud.utils

import dk.sdu.cloud.debug.DebugContext
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.logD
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.service.Loggable
import java.lang.Process as JvmProcess
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@Deprecated("No longer available", level = DeprecationLevel.WARNING)
data class ProcessStreams(
    val stdin: Int? = null,
    val stdout: Int? = null,
    val stderr: Int? = null,
)

@Suppress("DEPRECATION")
@Deprecated("No longer available", level = DeprecationLevel.ERROR)
fun startProcess(
    args: List<String>,
    envs: List<String> = listOf(),
    createStreams: () -> ProcessStreams
): Int {
    error("No longer available")
}

@JvmInline
value class ProcessStatus(private val status: Int) {
    val isRunning: Boolean get() = status < 0
    fun getExitCode(): Int {
        if (isRunning) throw IllegalStateException("Process is still running")
        return status
    }
}

class Process(
    val jvm: JvmProcess
) {
    val stdin: OutputStream? = jvm.outputStream
    val stdout: InputStream? = jvm.inputStream
    val stderr: InputStream? = jvm.errorStream
    val pid: Long
        get() = jvm.pid()

    fun retrieveStatus(waitForExit: Boolean): ProcessStatus {
        if (waitForExit) {
            return ProcessStatus(jvm.waitFor())
        }
        val exitCode = try {
            jvm.exitValue()
        } catch (ex: IllegalThreadStateException) {
            -1
        }

        return ProcessStatus(exitCode)
    }
}

fun startProcess(
    args: List<String>,
    envs: List<String> = listOf(),
    attachStdin: Boolean = false,
    attachStdout: Boolean = true,
    attachStderr: Boolean = true,
    nonBlockingStdout: Boolean = false,
    nonBlockingStderr: Boolean = false,
    workingDir: File? = null,
): Process {
    val jvmProcess = ProcessBuilder().apply {
        command(args)
        environment().putAll(
            envs.associate {
                val key = it.substringBefore('=')
                val value = it.substringAfter('=')
                key to value
            }
        )

        if (workingDir != null) directory(workingDir)

        if (!attachStdout) redirectOutput(ProcessBuilder.Redirect.DISCARD)
        if (!attachStderr) redirectError(ProcessBuilder.Redirect.DISCARD)
    }.start()

    if (!attachStdin) jvmProcess.outputStream.close()

    return Process(jvmProcess)
}

class ProcessResult(
    val statusCode: Int,
    val stdout: ByteArray,
    val stderr: ByteArray,
)

fun startProcessAndCollectToMemory(
    args: List<String>,
    envs: List<String> = listOf(),
    stdin: InputStream? = null,
    stdoutMaxSizeInBytes: Int = 1024 * 1024,
    stderrMaxSizeIntBytes: Int = 1024 * 1024,
): ProcessResult {
    val process = startProcess(
        args,
        envs = envs,
        attachStdin = stdin != null,
        attachStdout = stdoutMaxSizeInBytes > 0,
        attachStderr = stdoutMaxSizeInBytes > 0,
        nonBlockingStdout = true,
        nonBlockingStderr = true,
    )

    if (stdin != null && process.stdin != null) {
        stdin.copyTo(process.stdin)
    }

    var stdoutPtr = 0
    var isStdoutOpen = true
    val stdoutBuffer = ByteArray(stdoutMaxSizeInBytes)

    var stderrPtr = 0
    var isStderrOpen = true
    val stderrBuffer = ByteArray(stderrMaxSizeIntBytes)

    var status = ProcessStatus(-1)
    while ((isStdoutOpen || isStderrOpen)) {
        if (process.stdout != null) {
            val bytesToRead = stdoutMaxSizeInBytes - stdoutPtr
            // TODO Close the process
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                if (process.stdout.available() > 0) {
                    val read = process.stdout.read(stdoutBuffer, stdoutPtr, bytesToRead)
                    if (read == -1) {
                        isStdoutOpen = false
                    } else {
                        stdoutPtr += read
                    }
                }
            } catch (ex: IOException) {
                isStdoutOpen = false
            }
        }

        if (process.stderr != null) {
            val bytesToRead = stderrMaxSizeIntBytes - stderrPtr
            // TODO Close the process
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                if (process.stderr.available() > 0) {
                    val read = process.stderr.read(stderrBuffer, stderrPtr, bytesToRead)
                    if (read == -1) {
                        isStderrOpen = false
                    } else {
                        stderrPtr += read
                    }
                }
            } catch (ex: IOException) {
                isStderrOpen = false
            }
        }

        status = process.retrieveStatus(waitForExit = false)
        if (status.isRunning) {
            Thread.sleep(15)
        } else {
            if ((process.stdout?.available() ?: 0) == 0 && (process.stderr?.available() ?: 0) == 0) {
                break
            }
            Thread.sleep(15)
        }
    }

    if (status.isRunning) {
        status = process.retrieveStatus(waitForExit = true)
    }

    process.stderr?.close()
    process.stdout?.close()
    process.stdin?.close()

    return ProcessResult(status.getExitCode(), stdoutBuffer.copyOf(stdoutPtr), stderrBuffer.copyOf(stderrPtr))
}

data class ProcessResultText(
    val statusCode: Int,
    val stdout: String,
    val stderr: String,
)

fun startProcessAndCollectToString(
    args: List<String>,
    envs: List<String> = listOf(),
    stdin: InputStream? = null,
    stdoutMaxSizeInBytes: Int = 1024 * 1024,
    stderrMaxSizeIntBytes: Int = 1024 * 1024,
): ProcessResultText {
    val res = startProcessAndCollectToMemory(args, envs, stdin, stdoutMaxSizeInBytes, stderrMaxSizeIntBytes)
    return ProcessResultText(res.statusCode, res.stdout.decodeToString().trim(), res.stderr.decodeToString().trim())
}

data class CommandBuilder(
    val executable: String,
    val args: MutableList<String> = mutableListOf(),
    val envs: MutableList<String> = mutableListOf()
) {
    init {
        args.add(executable)
    }

    fun addArg(arg: String, argValue: String? = null): CommandBuilder {
        args.add(arg)
        if (argValue != null) args.add(argValue)
        return this
    }

    fun addEnv(env: String, envValue: String): CommandBuilder {
        envs.add("${env}=${envValue}")
        return this
    }

    fun executeToText(): ProcessResultText {
        if (!fileIsExecutable(args[0])) {
            log.warn("${args[0]} is not executable")
        }

        return startProcessAndCollectToString(args, envs)
    }

    fun executeToBinary(): ProcessResult {
        if (!fileIsExecutable(args[0])) {
            log.warn("${args[0]} is not executable")
        }

        return startProcessAndCollectToMemory(args, envs)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

inline fun buildCommand(executable: String, block: CommandBuilder.() -> Unit): CommandBuilder {
    return CommandBuilder(executable).also(block)
}

suspend fun executeCommandToText(
    executable: String,
    additionalDebug: JsonElement? = null,
    block: CommandBuilder.() -> Unit
): ProcessResultText {
    val cmd = buildCommand(executable, block)

    val debugContext = DebugContext.create()

    debugSystem.logD(
        "Command: $executable",
        JsonObject.serializer(),
        JsonObject(mapOf(
            "executable" to JsonPrimitive(executable),
            "arguments" to JsonArray(cmd.args.map { JsonPrimitive(it) }),
            "env" to JsonArray(cmd.envs.map { JsonPrimitive(it) }),
            "extra" to (additionalDebug ?: JsonNull)
        )),
        MessageImportance.IMPLEMENTATION_DETAIL,
        debugContext
    )

    val result = cmd.executeToText()

    debugSystem.logD(
        "Exit (${result.statusCode})",
        JsonObject.serializer(),
        JsonObject(mapOf(
            "statusCode" to JsonPrimitive(result.statusCode),
            "stdout" to JsonPrimitive(result.stdout),
            "stderr" to JsonPrimitive(result.stderr),
        )),
        if (result.statusCode == 0) MessageImportance.THIS_IS_NORMAL
        else MessageImportance.THIS_IS_ODD,
        DebugContext.createWithParent(debugContext.id)
    )

    return result
}

inline fun executeCommandToBinary(executable: String, block: CommandBuilder.() -> Unit): ProcessResult {
    return buildCommand(executable, block).executeToBinary()
}
