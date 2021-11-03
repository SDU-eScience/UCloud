package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.*

import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2

import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

import dk.sdu.cloud.defaultMapper


@Serializable
data class SlurmJob(  val ucloudId: String,   val slurmId: String,  val partition: String = "normal",  val status: Int = 1) {
    fun toJson() : JsonObject {
        return defaultMapper.encodeToJsonElement( this ) as JsonObject
    }
}


typealias UcloudState =  JobState

@Serializable
data class Status ( val id:String, val ucloudStatus: UcloudState, val slurmStatus: String, val message: String  )   



@Serializable
data class JobsBrowseRequest(
    val filters: List<Criteria>,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = PaginationRequestV2Consistency.REQUIRE,
    override val itemsToSkip: Long? = null,
): WithPaginationRequestV2 {

    fun toJson() : JsonObject {
        return defaultMapper.encodeToJsonElement( this ) as JsonObject
    }

}


@Serializable
data class Criteria ( val field:String, val condition:String )