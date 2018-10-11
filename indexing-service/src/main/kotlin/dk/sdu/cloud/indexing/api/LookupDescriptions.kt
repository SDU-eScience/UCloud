package dk.sdu.cloud.indexing.api

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions

data class ReverseLookupRequest(val fileId: String) {
    constructor(fileIds: List<String>) : this(fileIds.joinToString(","))

    @get:JsonIgnore
    val allIds: List<String>
        get() = fileId.split(",")
}

data class ReverseLookupResponse(val canonicalPath: List<String?>)

object LookupDescriptions : RESTDescriptions("indexing") {
    const val baseContext: String = "/api/indexing/lookup"

    val reverseLookup = callDescription<ReverseLookupRequest, ReverseLookupResponse, CommonErrorMessage> {
        name = "reverseLookup"

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"reverse"
        }

        params {
            +boundTo(ReverseLookupRequest::fileId)
        }
    }
}
