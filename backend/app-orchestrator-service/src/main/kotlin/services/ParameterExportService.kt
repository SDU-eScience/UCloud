package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.JobSpecification
import dk.sdu.cloud.service.Loggable

data class ExportedParameters(
    val siteVersion: Int,
    val request: JobSpecification,
)

class ParameterExportService {
    fun exportParameters(parameters: JobSpecification): ExportedParameters {
        return ExportedParameters(VERSION, parameters)
    }

    companion object : Loggable {
        override val log = logger()
        const val VERSION = 2
    }
}
