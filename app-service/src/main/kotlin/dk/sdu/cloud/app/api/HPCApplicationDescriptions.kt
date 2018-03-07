package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByName
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.KafkaRequest

data class FindApplicationAndOptionalDependencies(
    val name: String,
    val version: String,
    val resolve: Boolean?
)

object HPCApplicationDescriptions : RESTDescriptions(AppServiceDescription) {
    private const val baseContext = "/api/hpc/apps/"

    val findByName = callDescription<FindByName, List<ApplicationDescription>, CommonErrorMessage> {
        prettyName = "appsFindByName"
        path {
            using(baseContext)
            +boundTo(FindByName::name)
        }
    }

    val findByNameAndVersion = callDescription<
            FindApplicationAndOptionalDependencies,
            ApplicationWithOptionalDependencies,
            CommonErrorMessage> {
        prettyName = "appsFindByNameAndVersion"
        path {
            using(baseContext)
            +boundTo(FindApplicationAndOptionalDependencies::name)
            +boundTo(FindApplicationAndOptionalDependencies::version)
        }

        params {
            +boundTo(FindApplicationAndOptionalDependencies::resolve)
        }
    }

    val listAll = callDescription<Unit, List<ApplicationSummary>, CommonErrorMessage> {
        prettyName = "appsListAll"
        path { using(baseContext) }
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
