package dk.sdu.cloud.bare.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.ktor.http.HttpMethod

data class CreateEntityRequest(val text: String)
data class Entity(val text: String, val id: Long)

object EntityDescriptions : RESTDescriptions(BareServiceDescription) {
    val baseContext = "/api/bare/entity"

    val create = callDescription<CreateEntityRequest, Entity, CommonErrorMessage> {
        prettyName = "entityCreate"
        method = HttpMethod.Post

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val list = callDescription<PaginationRequest, Page<Entity>, CommonErrorMessage> {
        prettyName = "entityList"
        method = HttpMethod.Get

        path {
            using(baseContext)
        }

        params {
            +boundTo(PaginationRequest::itemsPerPage)
            +boundTo(PaginationRequest::page)
        }
    }
}