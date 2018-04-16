package dk.sdu.cloud.storage.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.KafkaRequest
import io.netty.handler.codec.http.HttpMethod

object ACLDescriptions : RESTDescriptions(StorageServiceDescription) {
    private const val baseContext = "/api/acl"

    val grantRights = callDescription<PermissionCommand.Grant, Unit, CommonErrorMessage> {
        prettyName = "grantRights"
        path { using(baseContext) }
        method = HttpMethod.PUT
        body { bindEntireRequestFromBody() }
    }

    val revokeRights = callDescription<PermissionCommand.Revoke, Unit, CommonErrorMessage> {
        prettyName = "revokeRights"
        path { using(baseContext) }
        method = HttpMethod.DELETE
        body { bindEntireRequestFromBody() }
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = PermissionCommand.Grant::class, name = "grant"),
    JsonSubTypes.Type(value = PermissionCommand.Revoke::class, name = "revoke")
)
sealed class PermissionCommand {
    abstract val onFile: String
    abstract val entity: String

    data class Grant(
        override val entity: String,
        override val onFile: String,
        val rights: AccessRight
    ) : PermissionCommand()

    data class Revoke(
        override val entity: String,
        override val onFile: String
    ) : PermissionCommand()
}
