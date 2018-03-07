package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.ext.irods.ICAT
import dk.sdu.cloud.storage.ext.irods.ICATAccessEntry
import org.slf4j.LoggerFactory
import java.sql.SQLException

class ICATService(private val icat: ICAT, private val icatZone: String) {
    fun createDirectDirectory(path: String, owner: String): Boolean {
        log.debug("Creating directory with direct strategy path=$path, owner=$owner")
        return icat.useConnection {
            val irodsPath = "/$icatZone$path"
            val parentPath = irodsPath.substringBeforeLast('/')
            val (userHasWriteAccess, parentAccessEntry) = userHasWriteAccess(
                owner,
                icatZone,
                parentPath
            )

            if (userHasWriteAccess && parentAccessEntry != null) {
                log.debug("Has write access and parent access entry is not null! $parentAccessEntry")
                log.debug("Registered collection for: $path and $owner in $icatZone")
                val objectId = try {
                    registerCollection(irodsPath, owner, icatZone)
                } catch (ex: SQLException) {
                    log.debug(ex.message)
                    return@useConnection false
                }

                log.debug("Got back result: $objectId")
                if (objectId != null) {
                    val now = System.currentTimeMillis()
                    log.debug("Registering access entry: $objectId, ${parentAccessEntry.userId}")

                    registerAccessEntry(
                        ICATAccessEntry(
                            objectId, parentAccessEntry.userId, 1200L,
                            now, now
                        )
                    )
                    return@useConnection true
                }
            }
            return@useConnection false
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ICATService::class.java)
    }
}