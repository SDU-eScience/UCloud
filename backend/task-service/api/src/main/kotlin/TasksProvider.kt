package dk.sdu.cloud.task.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.httpUpdate
import dk.sdu.cloud.calls.websocket
import kotlinx.serialization.builtins.serializer

class TasksProvider(namespace: String) : CallDescriptionContainer("task.provider.$namespace") {
    val baseContext = "/ucloud/$namespace/tasks"

    val userAction = call(
        "userAction",
        PostStatusRequest.serializer(),
        PostStatusResponse.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpUpdate(
            baseContext,
            "userAction",
            Roles.AUTHENTICATED
        )

        websocket(baseContext)
    }
}