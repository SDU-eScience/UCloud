package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.ktor.http.HttpMethod

data class FindApplicationAndOptionalDependencies(
    val name: String,
    val version: String
)

object HPCApplicationDescriptions : RESTDescriptions(AppServiceDescription) {
    const val baseContext = "/api/hpc/apps/"

    val findByName = callDescription<FindByNameAndPagination, Page<Application>, CommonErrorMessage> {
        prettyName = "appsFindByName"
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
        prettyName = "appsFindByNameAndVersion"
        path {
            using(baseContext)
            +boundTo(FindApplicationAndOptionalDependencies::name)
            +boundTo(FindApplicationAndOptionalDependencies::version)
        }
    }

    val listAll = callDescription<PaginationRequest, Page<Application>, CommonErrorMessage> {
        prettyName = "appsListAll"
        path { using(baseContext) }

        params {
            +boundTo(PaginationRequest::itemsPerPage)
            +boundTo(PaginationRequest::page)
        }
    }

    val create = callDescription<Unit, Unit, CommonErrorMessage> {
        prettyName = "appsCreate"
        method = HttpMethod.Put
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
