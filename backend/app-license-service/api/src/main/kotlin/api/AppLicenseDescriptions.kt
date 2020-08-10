package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class LicenseServerRequest(val serverId: String)

data class UpdateServerRequest(
    val name: String,
    val address: String,
    val port: Int,
    val license: String?,
    val withId: String
)

data class DetailedAccessEntityWithPermission(
    val entity: DetailedAccessEntity,
    val permission: ServerAccessRight
)

data class AccessEntityWithPermission(
    val entity: AccessEntity,
    val permission: ServerAccessRight
)

enum class ServerAccessRight {
    READ,
    READ_WRITE
}

data class Project(
    val id: String,
    val title: String
)

typealias ProjectGroup = Project

data class DetailedAccessEntity(
    val user: String?,
    val project: Project?,
    val group: ProjectGroup?
) {
    init {
        require(!user.isNullOrBlank() || (project != null && group != null)) { "No access entity defined" }
    }
}

data class AccessEntity(
    val user: String?,
    val project: String?,
    val group: String?
) {
    init {
        require(!user.isNullOrBlank() || (!project.isNullOrBlank() && !group.isNullOrBlank())) { "No access entity defined" }
    }
}

data class ProjectAndGroup(
    val project: String,
    val group: String
)

data class DeleteServerRequest(
    val id: String
)

data class NewServerRequest(
    val name: String,
    val address: String,
    val port: Int,
    val license: String?
)

data class ListAclRequest(
    val serverId: String
)

data class LicenseServerWithId(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val license: String?
)

data class LicenseServer(
    val name: String,
    val address: String,
    val port: Int,
    val license: String?
)

data class LicenseServerId(
    val id: String,
    val name: String
)

data class ListLicenseServersRequest(val tags: List<String>);
data class UpdateServerResponse(val serverId: String)
data class NewServerResponse(val serverId: String)

data class UpdateAclRequest(
    val serverId: String,
    val changes: List<AclEntryRequest>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

data class AclEntryRequest(
    val entity: AccessEntity,
    val rights: ServerAccessRight,
    val revoke: Boolean = false
)

object AppLicenseDescriptions : CallDescriptionContainer("app.license") {
    val baseContext = "/api/app/license"

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

    val list = call<ListLicenseServersRequest, List<LicenseServerId>, CommonErrorMessage>("list") {
        auth {
            roles = Roles.AUTHENTICATED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"list"
            }

            body { bindEntireRequestFromBody() }
        }
    }

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

    val delete = call<DeleteServerRequest, Unit, CommonErrorMessage>("update") {
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
