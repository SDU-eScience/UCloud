package dk.sdu.cloud.activity.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

object ActivityDescriptions : CallDescriptionContainer("activity") {
    val baseContext = "/api/activity"

    val listByFileId = call<ListActivityByIdRequest, ListActivityByIdResponse, CommonErrorMessage>("listByFileId") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +"by-file-id"
            }

            params {
                +boundTo(ListActivityByIdRequest::itemsPerPage)
                +boundTo(ListActivityByIdRequest::page)
                +boundTo(ListActivityByIdRequest::id)
            }
        }
    }

    val listByPath = call<ListActivityByPathRequest, ListActivityByPathResponse, CommonErrorMessage>("listByPath") {
        auth {
            access = AccessRight.READ
        }

        http {
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

    val listByUser = call<ListActivityByUserRequest, ListActivityByUserResponse, CommonErrorMessage>("listByUser") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ListActivityByUserRequest::itemsPerPage)
                +boundTo(ListActivityByUserRequest::page)
            }
        }
    }
}
