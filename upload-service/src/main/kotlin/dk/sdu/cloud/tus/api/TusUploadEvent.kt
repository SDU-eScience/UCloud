package dk.sdu.cloud.tus.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = TusUploadEvent.Created::class, name = "created"),
        JsonSubTypes.Type(value = TusUploadEvent.ChunkVerified::class, name = "chunk_verified"),
        JsonSubTypes.Type(value = TusUploadEvent.Completed::class, name = "completed"))
sealed class TusUploadEvent {
    abstract val id: String

    data class Created(
            override val id: String,
            val sizeInBytes: Long,
            val owner: String,
            val zone: String,
            val targetCollection: String,
            val targetName: String,
            val doChecksum: Boolean
    ) : TusUploadEvent()

    data class ChunkVerified(
            override val id: String,
            val chunk: Long
    ) : TusUploadEvent()

    data class Completed(override val id: String) : TusUploadEvent()
}