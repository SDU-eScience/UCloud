package dk.sdu.cloud.utils

import dk.sdu.cloud.debug.DebugContext
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.logD
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.forkAndReplace
import dk.sdu.cloud.wexitstatus
import dk.sdu.cloud.wifexited
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.system.exitProcess
import kotlinx.cinterop.ByteVar as KotlinxCinteropByteVar
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.json.*

data class ProcessStreams(
    val stdin: Int? = null,
    val stdout: Int? = null,
    val stderr: Int? = null,
)

fun replaceThisProcess(args: List<String>, newStreams: ProcessStreams, envs: List<String> = listOf()): Nothing {
    setsid()

    val nativeArgs = nativeHeap.allocArray<CPointerVar<KotlinxCinteropByteVar>>(args.size + 1)
    for (i in args.indices) {
        nativeArgs[i] = strdup(args[i])
    }
    nativeArgs[args.size] = null

    var nativeEnv = nativeHeap.allocArray<CPointerVar<KotlinxCinteropByteVar>>(envs.size + 1)
    for (i in envs.indices) {
        nativeEnv[i] = strdup(envs[i])
    }
    nativeEnv[envs.size] = null

    if (newStreams.stdin != null) {
        close(0)
        dup2(newStreams.stdin, 0)
        close(newStreams.stdin)
    }

    if (newStreams.stdout != null) {
        close(1)
        dup2(newStreams.stdout, 1)
        close(newStreams.stdout)
    }

    if (newStreams.stderr != null) {
        close(2)
        dup2(newStreams.stderr, 2)
        close(newStreams.stderr)
    }

    execve(args[0], nativeArgs, nativeEnv)
    exitProcess(255)
}

fun startProcess(
    args: List<String>,
    envs: List<String> = listOf(),
    createStreams: () -> ProcessStreams
): Int {
    val forkResult = fork()

    if (forkResult == -1) {
        throw IllegalStateException("Could not start new process")
    } else if (forkResult == 0) {
        replaceThisProcess(args, createStreams(), envs = envs)
    }

    return forkResult
}

value class ProcessStatus(private val status: Int) {
    val isRunning: Boolean get() = status < 0
    fun getExitCode(): Int {
        if (isRunning) throw IllegalStateException("Process is still running")
        return status
    }
}

class Process(
    val stdin: NativeOutputStream?,
    val stdout: NativeInputStream?,
    val stderr: NativeInputStream?,
    val pid: Int,
) {
    fun retrieveStatus(waitForExit: Boolean): ProcessStatus {
        memScoped {
            val wstatus = alloc<IntVar>()
            val result = waitpid(pid, wstatus.ptr, if (waitForExit) 0 else WNOHANG)
            return when {
                result == 0 -> ProcessStatus(-1)
                result < 0 -> throw IllegalStateException(getNativeErrorMessage(errno))
                else -> ProcessStatus(
                    if (wifexited(wstatus.value)) wexitstatus(wstatus.value)
                    else 255
                )
            }
        }
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
): Process {
    run {
        var stdinForChild: Int? = null
        var stdoutForChild: Int? = null
        var stderrForChild: Int? = null

        var stdinForParent: NativeOutputStream? = null
        var stdoutForParent: NativeInputStream? = null
        var stderrForParent: NativeInputStream? = null

        if (attachStdin) {
            val pipes = IntArray(2).also {
                it.usePinned { pipes ->
                    if (pipe(pipes.addressOf(0)) != 0) {
                        throw IllegalStateException("Failed to create stdin pipe: " + getNativeErrorMessage(errno))
                    }
                }
            }

            stdinForChild = pipes[0]
            stdinForParent = NativeOutputStream(pipes[1])
        }

        if (attachStdout) {
            val pipes = IntArray(2).also {
                it.usePinned { pipes ->
                    if (pipe(pipes.addressOf(0)) != 0) {
                        throw IllegalStateException("Failed to create stdout pipe: " + getNativeErrorMessage(errno))
                    }

                }
            }

            if (nonBlockingStdout && fcntl(pipes[0], F_SETFL, fcntl(pipes[0], F_GETFL) or O_NONBLOCK) != 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }

            stdoutForParent = NativeInputStream(pipes[0])
            stdoutForChild = pipes[1]
        }

        if (attachStderr) {
            val pipes = IntArray(2).also {
                it.usePinned { pipes ->
                    if (pipe(pipes.addressOf(0)) != 0) {
                        throw IllegalStateException("Failed to create stderr pipe: " + getNativeErrorMessage(errno))
                    }
                }
            }

            if (nonBlockingStderr && fcntl(pipes[0], F_SETFL, fcntl(pipes[0], F_GETFL) or O_NONBLOCK) != 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }
            stderrForParent = NativeInputStream(pipes[0])
            stderrForChild = pipes[1]
        }

        val nativeArgs = nativeHeap.allocArray<CPointerVar<KotlinxCinteropByteVar>>(args.size + 1)
        for (i in args.indices) {
            nativeArgs[i] = strdup(args[i])
        }
        nativeArgs[args.size] = null

        var nativeEnv = nativeHeap.allocArray<CPointerVar<KotlinxCinteropByteVar>>(envs.size + 1)
        for (i in envs.indices) {
            nativeEnv[i] = strdup(envs[i])
        }

        try {
            val pid =
                forkAndReplace(nativeArgs, nativeEnv, stdinForChild ?: 0, stdoutForChild ?: 0, stderrForChild ?: 0)
            if (pid < 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }
            if (stdinForChild != null) close(stdinForChild)
            if (stdoutForChild != null) close(stdoutForChild)
            if (stderrForChild != null) close(stderrForChild)

            return Process(stdinForParent, stdoutForParent, stderrForParent, pid)
        } finally {
            nativeHeap.free(nativeArgs)
            nativeHeap.free(nativeEnv)
        }
    }
}

class ProcessResult(
    val statusCode: Int,
    val stdout: ByteArray,
    val stderr: ByteArray,
)

fun startProcessAndCollectToMemory(
    args: List<String>,
    envs: List<String> = listOf(),
    stdin: NativeInputStream? = null,
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

    var status: ProcessStatus = ProcessStatus(-1)
    var breakOnNextIteration = false
    while (breakOnNextIteration || ((isStdoutOpen || isStderrOpen) && status.isRunning)) {
        if (process.stdout != null) {
            val bytesToRead = stdoutMaxSizeInBytes - stdoutPtr
            // TODO Close the process
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                val read = process.stdout.read(stdoutBuffer, stdoutPtr, bytesToRead)

                val error = read.getErrorOrNull()
                if (error != EAGAIN && error != EWOULDBLOCK) {
                    stdoutPtr += read.getOrThrow()
                }
            } catch (ex: ReadException.EndOfFile) {
                isStdoutOpen = false
            }
        }

        if (process.stderr != null) {
            val bytesToRead = stderrMaxSizeIntBytes - stderrPtr
            // TODO Close the process
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                val read = process.stderr.read(stderrBuffer, stderrPtr, bytesToRead)
                val error = read.getErrorOrNull()
                if (error != EAGAIN && error != EWOULDBLOCK) {
                    stderrPtr += read.getOrThrow()
                }
            } catch (ex: ReadException.EndOfFile) {
                isStderrOpen = false
            }
        }

        if (breakOnNextIteration) break
        status = process.retrieveStatus(waitForExit = false)
        if (status.isRunning) {
            usleep(1000U)
        } else {
            breakOnNextIteration = true
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
    stdin: NativeInputStream? = null,
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