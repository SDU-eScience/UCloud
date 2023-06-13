package dk.sdu.cloud.avatars.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.httpUpdate
import kotlinx.serialization.builtins.serializer

object BinaryTest : CallDescriptionContainer("avatars.binary") {
    val noRequest = call("noRequest", Unit.serializer(), Avatar.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate("/api/avatars/test", "noRequest", roles = Roles.PUBLIC)
    }

    val echo = call("echo", Avatar.serializer(), Avatar.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate("/api/avatars/test", "echo", roles = Roles.PUBLIC)
    }

    val callThroughEcho = call("callThroughEcho", Avatar.serializer(), Avatar.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate("/api/avatars/test", "callThroughEcho", roles = Roles.PUBLIC)
    }
}
