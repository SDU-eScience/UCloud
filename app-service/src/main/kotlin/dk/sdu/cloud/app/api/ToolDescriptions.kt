package dk.sdu.cloud.app.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class FindByNameAndPagination(
    val name: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

object ToolDescriptions : CallDescriptionContainer("hpc.tools") {
    val baseContext = "/api/hpc/tools"

    val findByNameAndVersion = call<FindByNameAndVersion, Tool, CommonErrorMessage>("findByNameAndVersion") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +boundTo(FindByNameAndVersion::name)
                +boundTo(FindByNameAndVersion::version)
            }
        }
    }

    val findByName = call<FindByNameAndPagination, Page<Tool>, CommonErrorMessage>("findByName") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +boundTo(FindByNameAndPagination::name)
            }

            params {
                +boundTo(FindByNameAndPagination::itemsPerPage)
                +boundTo(FindByNameAndPagination::page)
            }
        }
    }

    val listAll = call<PaginationRequest, Page<Tool>, CommonErrorMessage>("listAll") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
            }

            params {
                +boundTo(PaginationRequest::itemsPerPage)
                +boundTo(PaginationRequest::page)
            }
        }
    }

    val create = call<Unit, Unit, CommonErrorMessage>("create") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            /*
        body {
            // YAML document TODO Need support in implement feature for this
        }
        */
        }
    }
}
