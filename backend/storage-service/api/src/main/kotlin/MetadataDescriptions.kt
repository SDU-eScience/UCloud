package dk.sdu.cloud.file.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class MetadataUpdate(
    val path: String,
    val type: String,
    val username: String?,
    val jsonPayload: String
)

data class UpdateMetadataRequest(val updates: List<MetadataUpdate>)
typealias UpdateMetadataResponse = Unit

data class CreateMetadataRequest(val updates: List<MetadataUpdate>)
typealias CreateMetadataResponse = Unit

data class FindMetadataRequest(
    val path: String?,
    val type: String?,
    val username: String?
) {
    init {
        require(path != null || type != null || username != null) { "At least one argument must be non-null!" }
    }
}

data class FindMetadataResponse(val metadata: List<MetadataUpdate>)

data class RemoveMetadataRequest(val updates: List<FindMetadataRequest>)
typealias RemoveMetadataResponse = Unit

data class VerifyRequest(val paths: List<String>)
typealias VerifyResponse = Unit

data class FindByPrefixRequest(val pathPrefix: String, val username: String?, val type: String?)
typealias FindByPrefixResponse = FindMetadataResponse

object MetadataDescriptions : CallDescriptionContainer("files.metadata") {
    private const val baseContext = "/api/files/metadata"

    val updateMetadata = call<UpdateMetadataRequest, UpdateMetadataResponse, CommonErrorMessage>("updateMetadata") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val createMetadata = call<CreateMetadataRequest, CreateMetadataResponse, CommonErrorMessage>("createMetadata") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path { using(baseContext) }

            body { bindEntireRequestFromBody() }
        }
    }

    val findMetadata = call<FindMetadataRequest, FindMetadataResponse, CommonErrorMessage>("findMetadata") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"find"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val removeMetadata = call<RemoveMetadataRequest, RemoveMetadataResponse, CommonErrorMessage>("removeMetadata") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val verify = call<VerifyRequest, VerifyResponse, CommonErrorMessage>("verify") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"verify"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val findByPrefix = call<FindByPrefixRequest, FindByPrefixResponse, CommonErrorMessage>("findByPrefix") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"by-prefix"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
