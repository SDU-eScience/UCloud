package dk.sdu.cloud.activity.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions

object ActivityDescriptions : RESTDescriptions("activity") {
    val baseContext = "/api/activity"

    val listByFileId = callDescription<ListActivityByIdRequest, ListActivityByIdResponse, CommonErrorMessage> {
        name = "listByFileId"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"by-header"
            +boundTo(ListActivityByIdRequest::id)
        }

        params {
            +boundTo(ListActivityByIdRequest::itemsPerPage)
            +boundTo(ListActivityByIdRequest::page)
        }
    }

    val listByPath = callDescription<ListActivityByPathRequest, ListActivityByPathResponse, CommonErrorMessage> {
        name = "listByPath"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"by-path"
        }

        params {
            +boundTo(ListActivityByPathRequest::itemsPerPage)
            +boundTo(ListActivityByPathRequest::page)
            +boundTo(ListActivityByPathRequest::path)
        }
    }
}