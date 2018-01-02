package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.FindByName
import dk.sdu.cloud.service.KafkaRequest
import io.netty.handler.codec.http.HttpMethod
import dk.sdu.cloud.client.KafkaCallDescriptionBundle
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody

object HPCApplicationDescriptions : RESTDescriptions(AppServiceDescription) {
    private val baseContext = "/api/hpc/apps/"

    val findByName = callDescription<FindByName, List<ApplicationDescription>, List<ApplicationDescription>> {
        path {
            using(baseContext)
            +boundTo(FindByName::name)
        }
    }

    val findByNameAndVersion = callDescription<FindByNameAndVersion, ApplicationDescription, String> {
        path {
            using(baseContext)
            +boundTo(FindByNameAndVersion::name)
            +boundTo(FindByNameAndVersion::version)
        }
    }

    val listAll = callDescription<Unit, List<ApplicationDescription>, List<ApplicationDescription>> {
        path { using(baseContext) }
    }


}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = KafkaRequest.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = AppRequest.Start::class, name = "start"),
        JsonSubTypes.Type(value = AppRequest.Cancel::class, name = "cancel"))
sealed class AppRequest {
    data class Start(val application: NameAndVersion, val parameters: Map<String, Any>) : AppRequest()

    data class Cancel(val jobId: Long) : AppRequest()
}
