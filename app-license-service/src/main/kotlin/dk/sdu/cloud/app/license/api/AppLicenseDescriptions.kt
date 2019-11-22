package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class LicenseServerRequest(val licenseId: String)

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

data class AddApplicationsToServerResponse(val serverId: String)


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

data class UpdateServerResponse(val licenseId: String)
data class NewServerResponse(val licenseId: String)

data class UpdateAclResponse(val echo: String)
data class UpdateAclRequest(
    val licenseId: String,
    val changes: List<ACLEntryRequest>
) {
    init {
        if (changes.isEmpty()) throw IllegalArgumentException("changes cannot be empty")
        if (changes.size > 1000) throw IllegalArgumentException("Too many new entries")
    }
}

data class ACLEntryRequest(
    val entity: UserEntity,
    val rights: AccessRight,
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

            body { bindEntireRequestFromBody() }
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
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val updateAcl = call<UpdateAclRequest, UpdateAclResponse, CommonErrorMessage>("updateAcl") {
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

    val update = call<UpdateServerRequest, UpdateServerResponse, CommonErrorMessage>("save") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"save"
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

            params {
                +boundTo(NewServerRequest::name)
                +boundTo(NewServerRequest::version)
                +boundTo(NewServerRequest::address)
                +boundTo(NewServerRequest::license)
                +boundTo(NewServerRequest::applications)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
