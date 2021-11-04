package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class SlurmJob(
    val ucloudId: String,
    val slurmId: String,
    val partition: String = "normal",
    val status: Int = 1
) {
    fun toJson(): JsonObject {
        return defaultMapper.encodeToJsonElement(this) as JsonObject
    }
}

typealias UCloudState = JobState

@Serializable
data class SlurmStatus(
    val id: String,
    val ucloudState: UCloudState,
    val slurmStatus: String,
    val message: String
)

@Serializable
data class JobsBrowseRequest(
    val filters: List<Criteria>,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = PaginationRequestV2Consistency.REQUIRE,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2 {
    fun toJson(): JsonObject {
        return defaultMapper.encodeToJsonElement(this) as JsonObject
    }
}
