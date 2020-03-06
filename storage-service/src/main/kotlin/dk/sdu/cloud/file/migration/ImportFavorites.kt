package dk.sdu.cloud.file.migration

import com.fasterxml.jackson.annotation.JsonProperty
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.services.acl.MetadataDao
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.delay
import java.io.File
import dk.sdu.cloud.file.services.acl.Metadata as UMetadata

class ImportFavorites(
    private val db: AsyncDBSessionFactory,
    private val metadata: MetadataDao
) {
    private data class FavoritePayload(
        @get:JsonProperty("isFavorite")
        val isFavorite: Boolean
    )

    suspend fun runDataMigration() {
        val inputFile = File("/tmp/input.txt")
        log.info("Waiting for file at '${inputFile.absolutePath}'")
        while (!inputFile.exists()) {
            delay(100)
        }

        log.info("File is ready!")

        db.withTransaction { session ->
            inputFile.readLines().chunked(2).forEach { (path, username) ->
                metadata.createMetadata(
                    session,
                    UMetadata(path, "favorite", username, defaultMapper.valueToTree(FavoritePayload(true)))
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
