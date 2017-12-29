package dk.sdu.cloud.storage.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByName
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.storage.model.User

object GroupDescriptions : RESTDescriptions(StorageServiceDescription) {
    private val baseContext = "/api/groups"
    val findByName = callDescription<FindByName, List<User>, CommonErrorMessage> {
        path { using(baseContext) }
        params {
            +boundTo(FindByName::name)
        }
    }
}