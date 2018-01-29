package dk.sdu.cloud.storage.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.storage.model.AccessControlList // TODO....
import io.netty.handler.codec.http.HttpMethod

object ACLDescriptions : RESTDescriptions(StorageServiceDescription) {
    private val baseContext = "/api/acl"

    val listAtPath = callDescription<FindByPath, AccessControlList, CommonErrorMessage> {
        prettyName = "aclListAtPath"
        path { using(baseContext) }
        params { +boundTo(FindByPath::path) }
    }

    // TODO Temporary
    val grantRights = kafkaDescription<GrantPermissions> {
        prettyName = "aclGrantRights"
        path { using(baseContext) }
        method = HttpMethod.PUT
        body { bindEntireRequestFromBody() }
    }
}

// TODO Temporary
enum class TemporaryRight {
    READ,
    READ_WRITE,
    OWN
}

// TODO Temporary
data class GrantPermissions(val toUser: String, val onFile: String, val rights: TemporaryRight)