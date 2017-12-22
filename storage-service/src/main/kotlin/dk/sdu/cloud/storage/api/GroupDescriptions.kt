package dk.sdu.cloud.storage.api

import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.storage.model.User

object GroupDescriptions : RESTDescriptions() {
    private val baseContext = "/api/groups"
    val findByName = callDescription<FindByName, List<User>, Any> {
        path { using(baseContext) }
        params {
            +boundTo(FindByName::name)
        }
    }
}