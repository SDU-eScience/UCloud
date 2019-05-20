package dk.sdu.cloud.file.services.background

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import java.util.*

typealias BackgroundWorker = (requestType: String, requestMessage: String) -> BackgroundResponse

class BackgroundExecutor<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: BackgroundJobDao<Session>,
    private val streams: BackgroundStreams,
    private val streamService: EventStreamService
) {
    private val producer: EventProducer<BackgroundRequest> by lazy { streamService.createProducer(streams.stream) }
    private val handlers = HashMap<String, BackgroundWorker>()
    private var started: Boolean = false

    fun init() {
        started = true
        BackgroundScope.init()
        streamService.subscribe(
            stream = streams.stream,
            rescheduleIdleJobsAfterMs = 1000 * 60 * 60L,
            consumer = EventConsumer.Immediate { job ->
                val response =
                    handlers[job.requestType]?.invoke(job.requestType, job.requestMessage) ?: return@Immediate

                db.withTransaction { session ->
                    dao.setResponse(session, job.jobId, response)
                }
            }
        )
    }

    fun addWorker(requestType: String, worker: BackgroundWorker) {
        if (started) throw IllegalStateException("Workers should be added before calling init()")
        synchronized(this) { handlers[requestType] = worker }
    }

    suspend fun addJobToQueue(requestType: String, message: Any): String =
        addJobToQueue(requestType, defaultMapper.writeValueAsString(message))

    suspend fun addJobToQueue(requestType: String, requestMessage: String): String {
        val jobId = UUID.randomUUID().toString()
        val request = BackgroundRequest(jobId, requestType, requestMessage)
        db.withTransaction { session -> dao.create(session, request) }
        producer.produce(request)
        return jobId
    }

    fun queryStatus(jobId: String): BackgroundJob {
        return db.withTransaction { session ->
            dao.findOrNull(session, jobId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }
}
