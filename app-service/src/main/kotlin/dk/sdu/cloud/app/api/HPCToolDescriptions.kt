package dk.sdu.cloud.app.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class FindByNameAndPagination(
    val name: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

object HPCToolDescriptions : RESTDescriptions("hpc.tools") {
    val baseContext = "/api/hpc/tools"

    val findByNameAndVersion = callDescription<FindByNameAndVersion, Tool, CommonErrorMessage> {
        name = "toolsByNameAndVersion"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +boundTo(FindByNameAndVersion::name)
            +boundTo(FindByNameAndVersion::version)
        }
    }

    val findByName = callDescription<FindByNameAndPagination, Page<Tool>, CommonErrorMessage> {
        name = "toolsByName"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +boundTo(FindByNameAndPagination::name)
        }

        params {
            +boundTo(FindByNameAndPagination::itemsPerPage)
            +boundTo(FindByNameAndPagination::page)
        }
    }

    val listAll = callDescription<PaginationRequest, Page<Tool>, CommonErrorMessage> {
        name = "toolsListAll"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(PaginationRequest::itemsPerPage)
            +boundTo(PaginationRequest::page)
        }
    }

    val create = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "toolsCreate"
        method = HttpMethod.Put

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

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
