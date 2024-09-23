package dk.sdu.cloud.task.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import kotlinx.serialization.builtins.serializer

class TasksProvider(namespace: String) : CallDescriptionContainer("task.provider.$namespace") {
    val baseContext = "/ucloud/$namespace/tasks"

    val pauseOrCancel = call(
        "pauseOrCancel",
        BackgroundTask.serializer(),
        Unit.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(
            baseContext,
            "pauseOrCancel",
            Roles.PRIVILEGED
        )
    }
}