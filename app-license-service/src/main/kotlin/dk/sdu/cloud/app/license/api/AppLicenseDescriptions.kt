package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.services.acl.ServerAccessRight
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class LicenseServerRequest(val serverId: String)

data class UpdateServerRequest(
    val name: String,
    val version: String,
    val address: String,
    val port: String,
    val license: String?,
    val withId: String
)

data class AddApplicationsToServerRequest(
    val applications: List<Application>,
    val serverId: String
)

data class NewServerRequest(
    val name: String,
    val version: String,
    val address: String,
    val port: String,
    val license: String?,
    val applications: List<Application>?
)

data class Application(
    val name: String,
    val version: String
)

data class UpdateServerResponse(val serverId: String)
data class NewServerResponse(val serverId: String)

data class UpdateAclRequest(
    val serverId: String,
    val changes: List<ACLEntryRequest>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

data class ACLEntryRequest(
    val entity: UserEntity,
    val rights: ServerAccessRight,
    val revoke: Boolean = false
)

object AppLicenseDescriptions : CallDescriptionContainer("app.license") {
    val baseContext = "/api/app/license"

    val get = call<LicenseServerRequest, ApplicationLicenseServer, CommonErrorMessage>("get") {
        auth {
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

    val listByApp = call<Application, List<ApplicationLicenseServer>, CommonErrorMessage>("listByApp") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list-by-app"
            }

            params {
                +boundTo(Application::name)
                +boundTo(Application::version)
            }
        }
    }

    val updateAcl = call<UpdateAclRequest, Unit, CommonErrorMessage>("updateAcl") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update-acl"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val update = call<UpdateServerRequest, UpdateServerResponse, CommonErrorMessage>("update") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update"
            }

            params {
                +boundTo(UpdateServerRequest::name)
                +boundTo(UpdateServerRequest::version)
                +boundTo(UpdateServerRequest::address)
                +boundTo(UpdateServerRequest::license)
                +boundTo(UpdateServerRequest::withId)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val new = call<NewServerRequest, NewServerResponse, CommonErrorMessage>("new") {
        auth {
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
