package dk.sdu.cloud.task.api

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

class TaskHelperContext @PublishedApi internal constructor(
    private val serviceClient: AuthenticatedClient,
    private val scope: CoroutineScope,
    private val title: String,
    private val owner: String,

    private val updateFrequencyMs: Long = 1_000
) {
    var speeds: List<Speed> = emptyList()
    var progress: Progress? = null
    var status: String? = null

    private val buffer = StringBuilder()
    private var nextUpdate: Long = 0
    private val bufferMutex = Mutex()
    private var isComplete: Boolean = false

    init {
        require(updateFrequencyMs < 1000L * 60 * 5) { "Updates must occur at least once every five minutes" }
    }

    private fun setNextUpdate() {
        nextUpdate = System.currentTimeMillis() + updateFrequencyMs
    }

    suspend fun writeln(message: String) {
        bufferMutex.withLock {
            buffer.appendln(message)
        }
    }

    fun writelnBlocking(message: String) {
        runBlocking {
            writeln(message)
        }
    }

    fun markAsComplete() {
        isComplete = true
    }

    @PublishedApi
    internal fun initialize() {
        scope.launch {
            val taskId = Tasks.create.call(
                CreateRequest(
                    title,
                    owner,
                    status
                ),
                serviceClient
            ).orNull()?.jobId ?: return@launch

            while (!isComplete) {
                if (System.currentTimeMillis() > nextUpdate) {
                    val messageToAppend = bufferMutex.withLock {
                        val messageToAppend = buffer.toString()
                        buffer.clear()
                        messageToAppend
                    }

                    Tasks.postStatus.call(
                        PostStatusRequest(
                            TaskUpdate(
                                taskId,
                                title,
                                speeds,
                                progress,
                                isComplete,
                                messageToAppend,
                                status
                            )
                        ),
                        serviceClient
                    )

                    setNextUpdate()
                }

                // We don't want the co-routine to never exit so we loop at least once every 500ms
                delay(min(500, updateFrequencyMs))
            }
        }
    }
}

inline fun <R> runTask(
    serviceClient: AuthenticatedClient,
    scope: CoroutineScope,
    title: String,
    owner: String,
    updateFrequencyMs: Long = 1_000,
    autoComplete: Boolean = true,
    task: TaskHelperContext.() -> R
): R {
    val ctx = TaskHelperContext(serviceClient, scope, title, owner, updateFrequencyMs)
    ctx.initialize()
    val result = ctx.task()
    if (autoComplete) ctx.markAsComplete()
    return result
}
