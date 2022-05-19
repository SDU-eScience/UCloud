package dk.sdu.cloud.utils

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import dk.sdu.cloud.wifexited
import dk.sdu.cloud.wexitstatus
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

typealias WatchedProcessCallback = suspend (statusCode: Int) -> Unit
private data class WatchedProcess(
    val pid: Int,
    val killOnExit: Boolean,
    val onTermination: WatchedProcessCallback
)

object ProcessWatcher : Loggable {
    // NOTE(Dan): The ProcessWatcher is responsible for reaping background processes. This watcher should only be used
    // if no output is ever expected from the started process. For example, the L7 router should be added to this
    // process while a Slurm command should not.
    //
    // The interface is fairly simple, processes are added to a list of watched processes through `addWatch` or
    // `addWatchBlocking`. This class will monitor any watched process by repeatedly calling `waitpid` on it. If the
    // process has terminated, then `onTermination` (from `addWatch`) is invoked.
    //
    // If `killOnExit` is true, when added a process, then this process is automatically killed (via `SIGINT`) when the
    // parent process exits.

    override val log = logger()
    private val watchedProcesses = ArrayList<WatchedProcess>()
    private val mutex = Mutex()

    suspend fun addWatch(pid: Int, killOnExit: Boolean = true, onTermination: WatchedProcessCallback) {
        mutex.withLock {
            watchedProcesses.add(WatchedProcess(pid, killOnExit, onTermination))
        }
    }

    fun addWatchBlocking(pid: Int, killOnExit: Boolean = true, onTermination: WatchedProcessCallback) {
        runBlocking { addWatch(pid, killOnExit, onTermination) }
    }

    fun initialize() {
        ProcessingScope.launch {
            while (isActive) {
                try {
                    monitor()
                } catch (ex: Throwable) {
                    log.warn("Caught exception while monitoring processes:\n" + 
                        ex.stackTraceToString().prependIndent("  "))
                }
                delay(1000)
            }
        }
    }

    fun killAll() {
        log.debug("Terminating all children")
        runBlocking {
            mutex.withLock {
                memScoped {
                    val wstatus = alloc<IntVar>()
                    val iterator = watchedProcesses.iterator()
                    while (iterator.hasNext()) {
                        val process = iterator.next()
                        iterator.remove()

                        if (!process.killOnExit) continue

                        val result = waitpid(process.pid, wstatus.ptr, WNOHANG)
                        if (result <= 0) {
                            val ret = kill(process.pid, SIGINT)
                            if (ret != 0) {
                                log.info(buildString {
                                    append("Failed to kill child: ")
                                    appendLine(process.pid)

                                    append("  The return code of kill is ") 
                                    appendLine(ret)

                                    append("  errno is set to ")
                                    append(errno)
                                    append(" ")
                                    appendLine(getNativeErrorMessage(errno))

                                    appendLine("  You might not be able to restart the integration module until " + 
                                        "this child has been forcefully killed")
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun monitor() {
        // NOTE(Dan): Very important that we run the callbacks after the lock has been released. If we don't then
        // handlers are unable to actually add a new watch.
        val terminatedProcesses = ArrayList<Pair<Int, WatchedProcessCallback>>()

        mutex.withLock {
            memScoped {
                val wstatus = alloc<IntVar>()
                val iterator = watchedProcesses.iterator()
                while (iterator.hasNext()) {
                    val process = iterator.next()
                    val result = waitpid(process.pid, wstatus.ptr, WNOHANG)
                    when {
                        result == 0 -> {
                            // Still running
                        }

                        result < 0 -> {
                            // Something went wrong
                            log.warn("Error while monitoring process: ${process.pid}: " + 
                                getNativeErrorMessage(errno))

                            iterator.remove()
                        }

                        else -> {
                            val statusCode = 
                                if (wifexited(wstatus.value)) wexitstatus(wstatus.value)
                                else 255

                            terminatedProcesses.add(Pair(statusCode, process.onTermination))
                            iterator.remove()
                        }
                    }
                }
            }
        }

        for ((statusCode, onTermination) in terminatedProcesses) {
            onTermination(statusCode)
        }
    }
}

