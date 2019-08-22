package dk.sdu.cloud.file.processors

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.service.Loggable

private const val MAX_SCAN_REQUEST_AGE = 1000L * 60 * 60 * 24

data class ScanRequest(
    val rootToReference: Map<String, List<StorageFile>>,
    val createdAt: Long = System.currentTimeMillis()
)

object ScanStreams : EventStreamContainer() {
    val stream = stream<ScanRequest>("storage-scans", { "${it.createdAt}" })
}

class ScanProcessor(
    private val streams: EventStreamService,
    private val indexingService: IndexingService<*>
) {
    fun init() {
        streams.subscribe(ScanStreams.stream, EventConsumer.Immediate { request ->
            val now = System.currentTimeMillis()
            if (now - request.createdAt > MAX_SCAN_REQUEST_AGE) {
                return@Immediate
            }

            indexingService.runScan(request.rootToReference)
        })
    }

    companion object : Loggable {
        override val log = logger()
    }
}
