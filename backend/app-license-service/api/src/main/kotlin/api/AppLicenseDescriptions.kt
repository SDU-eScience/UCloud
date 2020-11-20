package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod

data class LicenseServerRequest(val serverId: String)

typealias UpdateServerRequest = LicenseServerWithId

data class DeleteServerRequest(
    val id: String
)

typealias NewServerRequest = LicenseServer

data class ListAclRequest(
    val serverId: String
)

/***
 * @see LicenseServer
 */
data class LicenseServerWithId(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val license: String?
)

/**
 * A license server
 *
 * Licenses servers in UCloud consist of a small number of fields which can be consumed by application to provide
 * the user access to a license server.
 *
 * A [LicenseServer] usually references a running TCP/IP server which can be contacted on [address]:[port] using some
 * application specific protocol. The [license] is intended to be used for authentication with the server.
 *
 * [LicenseServer]s are consumed by applications by using a parameter of type `license_server`. This should create a
 * widget which displays all [LicenseServer]s a user has access to that also have a specific tag (see [AddTagRequest]).
 */
data class LicenseServer(
    /**
     * A name of the license server used for user interfaces
     *
     * This does not uniquely identify the server.
     */
    val name: String,

    /**
     * An [address] used by the license server
     *
     * This is typically a hostname or an IP address. Use empty string if not applicable.
     */
    val address: String,

    /**
     * The [port] is used in conjunction the [address] to create an identifier usable to contact a TCP/IP server
     *
     * Use 0 if not applicable.
     */
    val port: Int,

    /**
     * A key used by the license server, typically for authentication
     *
     * This might not be applicable for the server, in that case use null.
     */
    val license: String?
)

data class LicenseServerId(
    val id: String,
    val name: String
)

data class FindByTagRequest(val tags: List<String>)
data class UpdateServerResponse(val serverId: String)
data class NewServerResponse(val serverId: String)

data class UpdateAclRequest(
    val serverId: String,
    val changes: List<AclChange>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

data class AclChange(
    val entity: AccessEntity,
    val rights: ServerAccessRight,
    val revoke: Boolean = false
)

@TSTopLevel
object AppLicenseDescriptions : CallDescriptionContainer("app.license") {
    val baseContext = "/api/app/license"

    /**
     * Finds a [LicenseServerWithId] by its [LicenseServerWithId.id]
     */
    val get = call<LicenseServerRequest, LicenseServerWithId, CommonErrorMessage>("get") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(LicenseServerRequest::serverId)
            }
        }
    }

    /**
     * Finds all relevant [LicenseServerWithId] that have any tag present in [FindByTagRequest.tags]
     */
    val findByTag = call<FindByTagRequest, List<LicenseServerId>, CommonErrorMessage>("findByTag") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"by-tag"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Lists _all_ license servers in the system
     *
     * This endpoint is only available for users in [Roles.PRIVILEGED]
     */
    val listAll = call<Unit, List<LicenseServerWithId>, CommonErrorMessage>("listAll") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listAll"
            }
        }
    }


    /**
     * Updates the ACL associated with the license server identified by [UpdateAclRequest.serverId]
     *
     * @see UpdateAclRequest
     * @see AclChange
     * @see AccessEntity
     */
    val updateAcl = call<UpdateAclRequest, Unit, CommonErrorMessage>("updateAcl") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"updateAcl"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Lists the ACL associated with a [LicenseServer] identified by [ListAclRequest.serverId]
     *
     * The ACL will contain a detailed summary of the access entities, suitable for user interfaces.
     */
    val listAcl = call<ListAclRequest, List<DetailedAccessEntityWithPermission>, CommonErrorMessage>("listAcl") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"listAcl"
            }

            params {
                +boundTo(ListAclRequest::serverId)
            }
        }
    }

    /**
     * Updates an existing [LicenseServer]
     */
    val update = call<UpdateServerRequest, UpdateServerResponse, CommonErrorMessage>("update") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val delete = call<DeleteServerRequest, Unit, CommonErrorMessage>("delete") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            params {
                +boundTo(DeleteServerRequest::id)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val new = call<NewServerRequest, NewServerResponse, CommonErrorMessage>("new") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"new"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
