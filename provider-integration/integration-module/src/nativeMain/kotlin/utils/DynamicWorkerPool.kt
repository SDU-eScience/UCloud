package dk.sdu.cloud.utils

import dk.sdu.cloud.service.Logger
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.freeze

class DynamicWorkerPool(
    private val name: String,
    private val minimumPoolSize: Int = 1,
    private val maximumPoolSize: Int = 128,
) {
    private val channel = Channel<WorkMessage<*>>(Channel.UNLIMITED)
    private val isRunning = atomic(false)

    init {
        freeze()
    }

    data class WorkMessage<T1>(
        val producer: () -> T1,
        val job: (T1) -> Unit,
    )

    fun <T1> execute(producer: () -> T1, job: (T1) -> Unit) {
        if (!isRunning.compareAndSet(expect = true, update = true)) {
            throw IllegalStateException("DynamicWorkerPool ($name) is not running!")
        }

        runBlocking { channel.send(WorkMessage(producer, job)) }
    }

    fun start() {
        if (!isRunning.compareAndSet(expect = false, update = true)) {
            throw IllegalStateException("Already running")
        }

        data class CapturedState(
            val name: String,
            val poolSize: Int,
            val maximumPoolSize: Int,
            val channel: Channel<WorkMessage<*>>,
            val isRunning: AtomicBoolean,
        )

        Worker.start(name = "$name (Pool Manager)").execute(
            TransferMode.UNSAFE,
            { CapturedState(name, minimumPoolSize, maximumPoolSize, channel, isRunning) },
            { (name, minSize, maxSize, channel, isRunning) ->
                val isAvailableForWork = Array(maxSize) { atomic(true).freeze() }
                val numberOfWorkersAvailable = Semaphore(maxSize).freeze()
                val availableWorkers = arrayOfNulls<Worker>(maxSize)
                (0 until minSize).forEach {
                    availableWorkers[it] = Worker.start(name = "$name [$it]")
                }

                while (isRunning.compareAndSet(expect = true, update = true)) {
                    val log = Logger(name).freeze()

                    @Suppress("UNCHECKED_CAST")
                    val nextMessage = runBlocking { channel.receiveOrNull() as WorkMessage<Any?>? } ?: break

                    data class Wrapper<T1>(
                        val isAvailable: AtomicBoolean,
                        val available: Semaphore,
                        val product: T1,
                        val job: (T1) -> Unit,
                        val log: Logger,
                    )

                    runBlocking { numberOfWorkersAvailable.acquire() }
                    var selectedWorker = -1
                    for (i in isAvailableForWork.indices) {
                        if (isAvailableForWork[i].compareAndSet(expect = true, update = false)) {
                            selectedWorker = i
                            break
                        }
                    }
                    if (selectedWorker == -1) throw IllegalStateException("State corruption in pool manager")

                    val isAvailable = isAvailableForWork[selectedWorker]
                    val worker = availableWorkers[selectedWorker]
                        ?: Worker.start(name = "$name [$selectedWorker]")
                            .also { availableWorkers[selectedWorker] = it }

                    // TODO Shutdown idle periodically workers

                    worker.execute(
                        TransferMode.UNSAFE,
                        {
                            Wrapper(
                                isAvailable,
                                numberOfWorkersAvailable,
                                nextMessage.producer(),
                                nextMessage.job,
                                log
                            )
                        },
                        { (workerAvailable, numberOfAvailable, params, job, log) ->
                            try {
                                job(params)
                            } catch (ex: Throwable) {
                                log.warn(ex.stackTraceToString())
                            } finally {
                                workerAvailable.getAndSet(true)
                                numberOfAvailable.release()
                            }
                        }
                    )
                }

                for (worker in availableWorkers) {
                    if (worker == null) continue
                    worker.requestTermination()
                }
            }
        )
    }

    fun close() {
        if (!isRunning.compareAndSet(expect = true, update = false)) {
            throw IllegalStateException("Not running")
        }
    }
}
