package dk.sdu.cloud.storage.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.storage.model.AccessControlList // TODO....
import io.netty.handler.codec.http.HttpMethod

object ACLDescriptions : RESTDescriptions(StorageServiceDescription) {
    private const val baseContext = "/api/acl"

    // TODO Temporary
    val grantRights = kafkaDescription<PermissionCommand.Grant> {
        prettyName = "aclGrantRights"
        path { using(baseContext) }
        method = HttpMethod.PUT
        body { bindEntireRequestFromBody() }
    }

    val revokeRights = kafkaDescription<PermissionCommand.Revoke> {
        prettyName = "aclRevokeRights"
        path { using(baseContext) }
        method = HttpMethod.DELETE
        body { bindEntireRequestFromBody() }
    }

    val aclUpdateBundle = listOf(grantRights, revokeRights)
}

// TODO Temporary
enum class TemporaryRight {
    READ,
    READ_WRITE,
    OWN
}

// TODO Temporary
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
    JsonSubTypes.Type(value = PermissionCommand.Grant::class, name = "grant"),
    JsonSubTypes.Type(value = PermissionCommand.Revoke::class, name = "revoke"))
sealed class PermissionCommand {
    abstract val onFile: String
    abstract val entity: String

    data class Grant(
        override val entity: String,
        override val onFile: String,
        val rights: TemporaryRight
    ) : PermissionCommand()

    data class Revoke(
        override val entity: String,
        override val onFile: String
    ) : PermissionCommand()
}
