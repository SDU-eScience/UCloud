package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.sql.ResultCursor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class SlurmJob(
    val ucloudId: String,
    val slurmId: String,
    val partition: String = "normal",
    val lastKnown: String = "init",
    val status: Int = 1,
) {
    fun toJson(): JsonObject {
        return defaultMapper.encodeToJsonElement(this) as JsonObject
    }
}

@Serializable
data class Criteria(val field: String, val condition: String)

@Serializable
data class SlurmAllocation(
    val jobId: String,
    val state: String,
    val exitCode: String,
    val start: String,
    val end: String
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


@Serializable
data class UcloudStateInfo(val state: JobState, val providerState: String , val message: String, val isFinal: Boolean )

@Serializable
class Mapping {
    companion object {
        val uCloudStates : HashMap<String, UcloudStateInfo> = hashMapOf<String, UcloudStateInfo> (

            "PENDING"       to UcloudStateInfo( JobState.IN_QUEUE, "PENDING"        , "Job is queued", false),
            "CONFIGURING"   to UcloudStateInfo( JobState.IN_QUEUE, "CONFIGURING"    , "Job is queued", false),
            "RESV_DEL_HOLD" to UcloudStateInfo( JobState.IN_QUEUE, "RESV_DEL_HOLD"  , "Job is queued", false),
            "REQUEUE_FED"   to UcloudStateInfo( JobState.IN_QUEUE, "REQUEUE_FED"    , "Job is queued", false),
            "SUSPENDED"     to UcloudStateInfo( JobState.IN_QUEUE, "SUSPENDED"      , "Job is queued", false),

            "REQUEUE_HOLD"  to UcloudStateInfo( JobState.IN_QUEUE, "REQUEUE_HOLD"   , "Job is held for requeue", false),
            "REQUEUED"      to UcloudStateInfo( JobState.IN_QUEUE, "REQUEUED"       , "Job is requeued", false),
            "RESIZING"      to UcloudStateInfo( JobState.IN_QUEUE, "RESIZING"       , "Job is resizing", false),

            "RUNNING"       to UcloudStateInfo( JobState.RUNNING, "RUNNING"         , "Job is running", false),
            "COMPLETING"    to UcloudStateInfo( JobState.RUNNING, "COMPLETING"      , "Job is running", false),
            "SIGNALING"     to UcloudStateInfo( JobState.RUNNING, "SIGNALING"       , "Job is running", false),
            "SPECIAL_EXIT"  to UcloudStateInfo( JobState.RUNNING, "SPECIAL_EXIT"    , "Job is running", false),
            "STAGE_OUT"     to UcloudStateInfo( JobState.RUNNING, "STAGE_OUT"       , "Job is running", false),

            "STOPPED"       to UcloudStateInfo( JobState.RUNNING, "STOPPED"         , "Job is stopped", false),

            "COMPLETED"     to UcloudStateInfo( JobState.SUCCESS, "COMPLETED"       , "Job is success", true),
            "CANCELLED"     to UcloudStateInfo( JobState.SUCCESS, "CANCELLED"       , "Job is success", true),
            "FAILED"        to UcloudStateInfo( JobState.SUCCESS, "FAILED"          , "Job is success", true),

            "OUT_OF_MEMORY" to UcloudStateInfo( JobState.SUCCESS, "OUT_OF_MEMORY"   , "Out of memory", true),

            "BOOT_FAIL"     to UcloudStateInfo( JobState.FAILURE, "BOOT_FAIL"       , "Job is failed", true),
            "NODE_FAIL"     to UcloudStateInfo( JobState.FAILURE, "NODE_FAIL"       , "Job is failed", true),

            "REVOKED"       to UcloudStateInfo( JobState.FAILURE, "REVOKED"         , "Job is revoked", true),
            "PREEMPTED"     to UcloudStateInfo( JobState.FAILURE, "PREEMPTED"       , "Preempted", true),
            "DEADLINE"      to UcloudStateInfo( JobState.EXPIRED, "DEADLINE"        , "Job is expired", true),
            "TIMEOUT"       to UcloudStateInfo( JobState.EXPIRED, "TIMEOUT"         , "Job is expired", true)

        )
    }
}


@Serializable
data class InteractiveSession(val token: String, val rank: Int , val ucloudId: String)