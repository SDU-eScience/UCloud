package dk.sdu.cloud.storage.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByName
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.storage.model.User // TODO

object UserDescriptions : RESTDescriptions(StorageServiceDescription) {
    private val baseContext = "/api/users"
    val findByName = callDescription<FindByName, User, CommonErrorMessage> {
        path { using(baseContext) }
        params {
            +boundTo(FindByName::name)
        }
    }
}