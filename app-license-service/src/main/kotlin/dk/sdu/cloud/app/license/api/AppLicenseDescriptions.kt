package dk.sdu.cloud.app.license.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.license.services.acl.EntityType
import dk.sdu.cloud.app.license.services.acl.UserEntity
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod

data class LicenseServerRequest(val licenseId: String)

data class SaveLicenseRequest(
    val name: String?,
    val version: String?,
    val address: String?,
    val license: String?,
    val withId: String?
)

data class SaveLicenseResponse(val licenseId: String)

data class UpdateAclResponse(val echo: String)
data class UpdateAclRequest(
    val licenseId: String,
    val changes: List<ACLEntryRequest>,
    val automaticRollback: Boolean? = null
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

    val save = call<SaveLicenseRequest, SaveLicenseResponse, CommonErrorMessage>("save") {
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
                +boundTo(SaveLicenseRequest::name)
                +boundTo(SaveLicenseRequest::version)
                +boundTo(SaveLicenseRequest::address)
                +boundTo(SaveLicenseRequest::license)
                +boundTo(SaveLicenseRequest::withId)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
