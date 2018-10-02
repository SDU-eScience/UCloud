package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class FindApplicationAndOptionalDependencies(
    val name: String,
    val version: String
)

data class SearchRequest(
    val query: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

object HPCApplicationDescriptions : RESTDescriptions("hpc.apps") {
    const val baseContext = "/api/hpc/apps/"

    val markAsFavorite = callDescription<FindApplicationAndOptionalDependencies, Unit, CommonErrorMessage> {
        name = "markAsFavorite"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"favorite"
            +boundTo(FindApplicationAndOptionalDependencies::name)
            +boundTo(FindApplicationAndOptionalDependencies::version)
        }
    }


    val unMarkAsFavorite = callDescription<FindApplicationAndOptionalDependencies, Unit, CommonErrorMessage> {
        name = "unMarkAsFavorite"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"unfavorite"
            +boundTo(FindApplicationAndOptionalDependencies::name)
            +boundTo(FindApplicationAndOptionalDependencies::version)
        }
    }

    val retrieveFavorites = callDescription<PaginationRequest, Page<Application>, CommonErrorMessage> {
        name = "retrieveFavorites"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"favorites"
        }

        params {
            +boundTo(PaginationRequest::itemsPerPage)
            +boundTo(PaginationRequest::page)
        }
    }
    val searchTag = callDescription<SearchRequest, Page<Application>, CommonErrorMessage> {
        name = "searchTags"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"searchTags"
        }

        params {
            +boundTo(SearchRequest::query)
            +boundTo(SearchRequest::itemsPerPage)
            +boundTo(SearchRequest::page)
        }
    }

    val search = callDescription<SearchRequest, Page<Application>, CommonErrorMessage> {
        name = "searchApps"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"search"
        }

        params {
            +boundTo(SearchRequest::query)
            +boundTo(SearchRequest::itemsPerPage)
            +boundTo(SearchRequest::page)
        }
    }

    val findByName = callDescription<FindByNameAndPagination, Page<Application>, CommonErrorMessage> {
        name = "appsFindByName"

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

    val findByNameAndVersion = callDescription<
            FindApplicationAndOptionalDependencies,
            Application,
            CommonErrorMessage> {
        name = "appsFindByNameAndVersion"

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +boundTo(FindApplicationAndOptionalDependencies::name)
            +boundTo(FindApplicationAndOptionalDependencies::version)
        }
    }

    val listAll = callDescription<PaginationRequest, Page<Application>, CommonErrorMessage> {
        name = "appsListAll"
        path { using(baseContext) }

        auth {
            access = AccessRight.READ
        }

        params {
            +boundTo(PaginationRequest::itemsPerPage)
            +boundTo(PaginationRequest::page)
        }
    }

    val create = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "appsCreate"
        method = HttpMethod.Put

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path { using(baseContext) }
        // body { //YAML Body TODO Implement support }
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AppRequest.Start::class, name = "start")
)
sealed class AppRequest {
    data class Start(
        val application: NameAndVersion,
        val parameters: Map<String, Any>,
        val numberOfNodes: Int? = null,
        val tasksPerNode: Int? = null,
        val maxTime: SimpleDuration? = null
    ) : AppRequest()
}
