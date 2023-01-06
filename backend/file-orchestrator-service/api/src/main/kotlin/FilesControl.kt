package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiStable
data class FilesControlAddUpdateRequestItem(
    val taskId: String,
    val update: String,
)

@Serializable
@UCloudApiStable
data class FilesControlMarkAsCompleteRequestItem(
    val taskId: String
)

@UCloudApiStable
object FilesControl : CallDescriptionContainer("files.control") {
    const val baseContext = "/api/files/control"

    val addUpdate = call("addUpdate", BulkRequest.serializer(FilesControlAddUpdateRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "addUpdate", roles = Roles.PROVIDER)
    }

    val markAsComplete = call("markAsComplete", BulkRequest.serializer(FilesControlMarkAsCompleteRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "markAsComplete", roles = Roles.PROVIDER)
    }
}
