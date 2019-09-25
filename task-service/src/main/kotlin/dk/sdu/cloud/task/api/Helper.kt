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

interface TaskContext {
    val title: String
    val owner: String
    var speeds: List<Speed>
    var progress: Progress?
    var status: String?

    suspend fun writeln(message: String)
    fun writelnBlocking(message: String)
    fun markAsComplete()
}

object DiscardingTaskContext : TaskContext {
    override val title: String = ""
    override val owner: String = ""
    override var speeds: List<Speed> = emptyList()
    override var progress: Progress? = null
    override var status: String? = null

    override suspend fun writeln(message: String) {
    }

    override fun writelnBlocking(message: String) {
    }

    override fun markAsComplete() {
    }
}

class RealTaskContext @PublishedApi internal constructor(
    private val serviceClient: AuthenticatedClient,
    private val scope: CoroutineScope,
    override val title: String,
    override val owner: String,

    private val updateFrequencyMs: Long = 1_000
) : TaskContext {
    override var speeds: List<Speed> = emptyList()
    override var progress: Progress? = null
    override var status: String? = null

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

    override suspend fun writeln(message: String) {
        bufferMutex.withLock {
            buffer.appendln(message)
        }
    }

    override fun writelnBlocking(message: String) {
        runBlocking {
            writeln(message)
        }
    }

    override fun markAsComplete() {
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

            Tasks.markAsComplete.call(MarkAsCompleteRequest(taskId), serviceClient)
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
    task: RealTaskContext.() -> R
): R {
    val ctx = RealTaskContext(serviceClient, scope, title, owner, updateFrequencyMs)
    ctx.initialize()
    try {
        return ctx.task()
    } finally {
        if (autoComplete) ctx.markAsComplete()
    }
}
