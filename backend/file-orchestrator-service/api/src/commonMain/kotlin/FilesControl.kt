package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class FilesControlAddUpdateRequestItem(
    val taskId: String,
    val update: String,
)

@Serializable
data class FilesControlMarkAsCompleteRequestItem(
    val taskId: String
)

object FilesControl : CallDescriptionContainer("files.control") {
    const val baseContext = "/api/files/control"

    val addUpdate = call<BulkRequest<FilesControlAddUpdateRequestItem>, Unit, CommonErrorMessage>("addUpdate") {
        httpUpdate(baseContext, "addUpdate", roles = Roles.PROVIDER)
    }

    val markAsComplete = call<BulkRequest<FilesControlMarkAsCompleteRequestItem>, Unit, CommonErrorMessage>("markAsComplete") {
        httpUpdate(baseContext, "markAsComplete", roles = Roles.PROVIDER)
    }
}