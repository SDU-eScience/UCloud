package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.service.Loggable

data class ExportedMount(val ref: String, val readOnly: Boolean)

data class ExportedParameters(
    val siteVersion: Int,
    val application: NameAndVersion,
    val parameters: Map<String, Any?>,
    val numberOfNodes: Int,
    val maxTime: SimpleDuration,
    val mountedFolders: List<ExportedMount>
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
            verifiedJob.mounts.map { ExportedMount(it.sourcePath, it.readOnly) }
        )
    }

    companion object : Loggable {
        override val log = logger()

        const val VERSION = 1
    }
}
