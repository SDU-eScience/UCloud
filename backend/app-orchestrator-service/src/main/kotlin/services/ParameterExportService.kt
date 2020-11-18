package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobParameters
import dk.sdu.cloud.service.Loggable

data class ExportedParameters(
    val siteVersion: Int,
    val request: JobParameters,
)

class ParameterExportService {
    fun exportParameters(parameters: JobParameters): ExportedParameters {
        return ExportedParameters(VERSION, parameters)
    }

    companion object : Loggable {
        override val log = logger()
        const val VERSION = 2
    }
}
