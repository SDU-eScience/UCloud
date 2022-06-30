package dk.sdu.cloud.utils

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.lang.Process as JvmProcess

typealias WatchedProcessCallback = suspend (statusCode: Int) -> Unit

private data class WatchedProcess(
    val pid: JvmProcess,
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

    suspend fun addWatch(process: Process, killOnExit: Boolean = true, onTermination: WatchedProcessCallback) {
        addWatch(process.jvm, killOnExit, onTermination)
    }

    fun addWatchBlocking(process: Process, killOnExit: Boolean = true, onTermination: WatchedProcessCallback) {
        runBlocking { addWatch(process.jvm, killOnExit, onTermination) }
    }

    suspend fun addWatch(process: JvmProcess, killOnExit: Boolean = true, onTermination: WatchedProcessCallback) {
        mutex.withLock {
            watchedProcesses.add(WatchedProcess(process, killOnExit, onTermination))
        }
    }

    fun addWatchBlocking(process: JvmProcess, killOnExit: Boolean = true, onTermination: WatchedProcessCallback) {
        runBlocking { addWatch(process, killOnExit, onTermination) }
    }

    fun initialize() {
        ProcessingScope.launch {
            while (isActive) {
                try {
                    monitor()
                } catch (ex: Throwable) {
                    log.warn(
                        "Caught exception while monitoring processes:\n" +
                            ex.stackTraceToString().prependIndent("  ")
                    )
                }
                delay(1000)
            }
        }
    }

    fun killAll() {
        log.debug("Terminating all children")
        runBlocking {
            mutex.withLock {
                val iterator = watchedProcesses.iterator()
                while (iterator.hasNext()) {
                    val process = iterator.next()
                    iterator.remove()

                    if (!process.killOnExit) continue

                    try {
                        process.pid.destroyForcibly().exitValue()
                    } catch (ex: Throwable) {
                        log.info(buildString {
                            append("Failed to kill child: ")
                            appendLine(process.pid)

                            appendLine(
                                "  You might not be able to restart the integration module until " +
                                    "this child has been forcefully killed"
                            )
                        })
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
            val iterator = watchedProcesses.iterator()
            while (iterator.hasNext()) {
                val process = iterator.next()
                val didExit = runCatching { process.pid.waitFor(0, TimeUnit.MILLISECONDS) }.getOrElse { false }

                if (didExit) {
                    val statusCode = runCatching { process.pid.exitValue() }.getOrElse { 255 }
                    terminatedProcesses.add(Pair(statusCode, process.onTermination))
                    iterator.remove()
                }
            }
        }

        for ((statusCode, onTermination) in terminatedProcesses) {
            onTermination(statusCode)
        }
    }
}
