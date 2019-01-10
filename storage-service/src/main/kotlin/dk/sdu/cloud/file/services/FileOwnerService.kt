package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.processors.StorageEventListener
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Loggable
import java.util.*

/**
 * A service for determining the real owner of a file.
 *
 * This works by looking up the root directory of the file and looking at the "creator" ([FileAttribute.OWNER]). The
 * results are cached for performance.
 */
class FileOwnerService<Ctx : FSUserContext>(
    private val commandRunner: FSCommandRunnerFactory<Ctx>,
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>
) : StorageEventListener<Ctx> {
    private val cache = Collections.synchronizedMap(HashMap<String, String>())

    override suspend fun isInterested(batch: List<StorageEvent>): Boolean =
        batch.any { isInterestedInEvent(it) }

    private fun isInterestedInEvent(event: StorageEvent): Boolean {
        when (event) {
            is StorageEvent.Moved -> {
                val newPathComponents = event.path.components()
                val oldPathComponents = event.oldPath.components()
                if (newPathComponents.size < 2 || newPathComponents[0] != "home") return true
                if (oldPathComponents.size < 2 || oldPathComponents[0] != "home") return true

                // We only need to change ownership if file has moved outside of root
                return newPathComponents[1] != oldPathComponents[1]
            }

            is StorageEvent.CreatedOrRefreshed -> return true

            else -> return false
        }
    }

    override suspend fun handleBatch(ctx: Ctx, batch: List<StorageEvent>) {
        batch
            .asSequence()
            .filter { isInterestedInEvent(it) }
            .forEach { event ->
                try {
                    writeFileOwner(ctx, event.path)
                } catch (ex: FSException.NotFound) {
                    log.debug("Could not find file ${event.path}")
                }
            }
    }

    suspend fun lookupOwner(path: String): String {
        val components = path.components()
        log.debug("$path components are $components")
        if (components.size >= 2 && components.first() == "home") {
            val rootDir = components[1]
            log.debug("Looking for result in $rootDir")

            val cachedResult = cache[rootDir]
            if (cachedResult != null) return cachedResult

            log.debug("Result not in cache. Looking up owner of root dir...")
            return commandRunner
                .withContext(SERVICE_USER) { ctx ->
                    coreFs.stat(ctx, joinPath(components[0], components[1]), setOf(FileAttribute.OWNER)).owner
                }.also {
                    log.debug("Directory is owned by $it")
                    cache[rootDir] = it
                }
        } else {
            log.debug("$path is outside of /home/. Not using cache!")
            return commandRunner.withContext(SERVICE_USER) { ctx ->
                coreFs.stat(ctx, path, setOf(FileAttribute.OWNER)).owner
            }
        }
    }

    private suspend fun writeFileOwner(ctx: Ctx, path: String): String {
        val realOwner = lookupOwner(path)
        fs.setExtendedAttribute(ctx, path, XATTRIBUTE, realOwner)
        return realOwner
    }

    companion object : Loggable {
        override val log = logger()

        const val XATTRIBUTE = "owner"
    }
}
