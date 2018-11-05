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
            +"by-file-id"
        }

        params {
            +boundTo(ListActivityByIdRequest::itemsPerPage)
            +boundTo(ListActivityByIdRequest::page)
            +boundTo(ListActivityByIdRequest::id)
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

    val streamByPath = callDescription<StreamByPathRequest, StreamByPathResponse, CommonErrorMessage> {
        name = "streamByPath"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"stream"
            +"by-path"
        }

        params {
            +boundTo(StreamByPathRequest::path)
            +boundTo(StreamByPathRequest::itemsPerPage)
            +boundTo(StreamByPathRequest::page)
        }
    }

    val streamForUser = callDescription<StreamForUserRequest, StreamForUserResponse, CommonErrorMessage> {
        name = "streamForUser"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"stream"
        }

        params {
            +boundTo(StreamForUserRequest::user)
            +boundTo(StreamForUserRequest::itemsPerPage)
            +boundTo(StreamForUserRequest::page)
        }
    }
}
