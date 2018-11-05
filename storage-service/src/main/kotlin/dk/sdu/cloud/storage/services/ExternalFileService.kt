package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.STORAGE_EVENT_MODE
import dk.sdu.cloud.storage.util.toCreatedEvent

/**
 * A service for dealing with files created by external systems
 */
class ExternalFileService<FSCtx : CommandRunner>(
    private val processRunner: FSCommandRunnerFactory<FSCtx>,
    private val fs: CoreFileSystemService<FSCtx>,
    private val eventProducer: StorageEventProducer
) {
    suspend fun scanFilesCreatedExternally(path: String) {
        val events = ArrayList<StorageEvent>()

        try {
            processRunner.withContext(SERVICE_USER) { ctx ->
                val rootStat = fs.stat(ctx, path, STORAGE_EVENT_MODE)
                if (rootStat.fileType == FileType.DIRECTORY) {
                    fs.tree(ctx, path, STORAGE_EVENT_MODE).forEach { file ->
                        events.add(file.toCreatedEvent())
                    }
                } else {
                    // tree call will include root (always)
                    events.add(rootStat.toCreatedEvent())
                }
            }

            events.forEach {
                eventProducer.emit(it)
            }
        } catch (ex: FSException) {
            log.debug("Caught exception while scanning external created files: $path")
            log.debug(ex.stackTraceToString())
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
