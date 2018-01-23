package dk.sdu.cloud.storage.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.storage.model.AccessControlList // TODO....

object ACLDescriptions : RESTDescriptions(StorageServiceDescription) {
    private val baseContext = "/api/acl"

    val listAtPath = callDescription<FindByPath, AccessControlList, CommonErrorMessage> {
        prettyName = "aclListAtPath"
        path { using(baseContext) }
        params { +boundTo(FindByPath::path) }
    }

}