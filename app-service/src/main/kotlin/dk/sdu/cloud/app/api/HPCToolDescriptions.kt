package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.storage.api.WithPagination

data class FindByNameAndPagination(
    val name: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPagination

object HPCToolDescriptions : RESTDescriptions(AppServiceDescription) {
    private val baseContext = "/api/hpc/tools"

    val findByNameAndVersion = callDescription<FindByNameAndVersion, Tool, CommonErrorMessage> {
        prettyName = "toolsByNameAndVersion"
        path {
            using(baseContext)
            +boundTo(FindByNameAndVersion::name)
            +boundTo(FindByNameAndVersion::version)
        }
    }

    val findByName = callDescription<FindByNameAndPagination, Page<Tool>, CommonErrorMessage> {
        prettyName = "toolsByName"
        path {
            using(baseContext)
            +boundTo(FindByNameAndPagination::name)
        }

        params {
            +boundTo(FindByNameAndPagination::itemsPerPage)
            +boundTo(FindByNameAndPagination::page)
        }
    }

    val listAll = callDescription<PaginationRequest, Page<Tool>, List<NormalizedToolDescription>> {
        prettyName = "toolsListAll"
        path {
            using(baseContext)
        }
    }
}