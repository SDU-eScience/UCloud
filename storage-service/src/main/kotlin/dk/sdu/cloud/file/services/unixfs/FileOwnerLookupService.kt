package dk.sdu.cloud.file.services.unixfs

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.unwrap
import dk.sdu.cloud.service.Loggable
import java.util.concurrent.ConcurrentHashMap

/**
 * A service for determining the real creator of a file.
 *
 * This works by looking up the root directory of the file and looking at the "creator" ([FileAttribute.CREATOR]). The
 * results are cached for performance.
 */
class FileOwnerLookupService<Ctx : CommandRunner>(
    private val commandRunner: FSCommandRunnerFactory<Ctx>,
    private val unixFs: LowLevelFileSystemInterface<Ctx>
) {
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun lookupOwner(path: String): String {
        val components = path.components()
        log.debug("$path components are $components")
        if (components.size >= 2 && components.first() == "home") {
            val rootDir = components[1]
            log.debug("Looking for result in $rootDir")

            val cachedResult = cache[rootDir]
            if (cachedResult != null) return cachedResult

            log.debug("Result not in cache. Looking up creator of root dir...")
            return commandRunner
                .withContext(SERVICE_USER) { ctx ->
                    unixFs.stat(ctx, joinPath(components[0], components[1]), setOf(FileAttribute.CREATOR)).unwrap().creator
                }.also {
                    log.debug("Directory is owned by $it")
                    cache[rootDir] = it
                }
        } else {
            log.debug("$path is outside of /home/. Not using cache!")
            return commandRunner.withContext(SERVICE_USER) { ctx ->
                unixFs.stat(ctx, path, setOf(FileAttribute.CREATOR)).unwrap().creator
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
