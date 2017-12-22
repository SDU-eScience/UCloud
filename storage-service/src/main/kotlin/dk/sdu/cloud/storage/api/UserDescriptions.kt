package dk.sdu.cloud.storage.api

import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.storage.model.User // TODO

data class FindByName(val name: String)
object UserDescriptions : RESTDescriptions() {
    private val baseContext = "/api/users"
    val findByName = callDescription<FindByName, User, Any> {
        path { using(baseContext) }
        params {
            +boundTo(FindByName::name)
        }
    }
}