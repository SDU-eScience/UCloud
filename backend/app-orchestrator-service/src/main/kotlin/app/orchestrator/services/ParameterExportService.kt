package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.service.Loggable

data class ExportedMount(val ref: String)

data class ExportedParameters(
    val siteVersion: Int,
    val application: NameAndVersion,
    val parameters: Map<String, Any?>,
    val numberOfNodes: Int,
    val maxTime: SimpleDuration,
    val mountedFolders: List<ExportedMount>,
    val jobName: String?,
    val machineType: Product.Compute
)

class ParameterExportService {
    fun exportParameters(verifiedJob: VerifiedJob, rawParameters: Map<String, Any?>): ExportedParameters {
        verifiedJob.peers
        return ExportedParameters(
            VERSION,
            NameAndVersion(verifiedJob.application.metadata.name, verifiedJob.application.metadata.version),
            rawParameters,
            verifiedJob.nodes,
            verifiedJob.maxTime,
            verifiedJob.mounts.map { ExportedMount(it.sourcePath) },
            verifiedJob.name,
            verifiedJob.reservation
        )
    }

    companion object : Loggable {
        override val log = logger()
        const val VERSION = 1
    }
}
