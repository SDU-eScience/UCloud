package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.STORAGE_EVENT_MODE
import dk.sdu.cloud.file.util.toCreatedEvent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.launch

/**
 * A service for dealing with files created by external systems
 */
class FileScanner<FSCtx : CommandRunner>(
    private val processRunner: FSCommandRunnerFactory<FSCtx>,
    private val fs: CoreFileSystemService<FSCtx>,
    private val eventProducer: StorageEventProducer
) {
    suspend fun scanFilesCreatedExternally(path: String) {
        log.debug("scanFilesCreatedExternally($path)")
        val events = ArrayList<StorageEvent>()

        try {
            processRunner.withContext(SERVICE_USER) { ctx ->
                val rootStat = fs.stat(ctx, path, STORAGE_EVENT_MODE)
                if (rootStat.fileType == FileType.DIRECTORY) {
                    fs.tree(ctx, path, STORAGE_EVENT_MODE).forEach { file ->
                        events.add(file.toCreatedEvent(copyCausedBy = true))
                    }
                } else {
                    // tree call will include root (always)
                    events.add(rootStat.toCreatedEvent(copyCausedBy = true))
                }
            }

            BackgroundScope.launch {
                log.info("Producing events: ${events}")
                eventProducer.produce(events)
                log.info("Events produced!")
            }.join()
        } catch (ex: FSException) {
            log.debug("Caught exception while scanning external created files: $path")
            log.debug(ex.stackTraceToString())
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
