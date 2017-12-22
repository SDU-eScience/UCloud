package dk.sdu.cloud.storage.api

import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.storage.model.AccessControlList // TODO....

object ACLDescriptions : RESTDescriptions() {
    private val baseContext = "/api/acl"

    val listAtPath = callDescription<FindByPath, AccessControlList, Any> {
        path { using(baseContext) }
        params { +boundTo(FindByPath::path) }
    }

}