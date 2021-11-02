package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.*
import kotlinx.serialization.Serializable

@Serializable
data class SlurmJob(
    val ucloudId: String,
    val slurmId: String,
    val partition: String = "normal",
    val status: Int = 1
)

typealias UCloudState = JobState

@Serializable
data class SlurmStatus(
    val id: String,
    val ucloudState: UCloudState,
    val slurmStatus: String,
    val message: String
)