package dk.sdu.cloud.abc.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.KafkaRequest
import io.netty.handler.codec.http.HttpMethod
import org.esciencecloud.client.KafkaCallDescriptionBundle
import org.esciencecloud.client.RESTDescriptions
import org.esciencecloud.client.bindEntireRequestFromBody

object HPCApplicationDescriptions : RESTDescriptions() {
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

    val startApp = kafkaDescription<AppRequest.Start> {
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"jobs"
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val cancelApp = kafkaDescription<AppRequest.Cancel> {
        method = HttpMethod.DELETE

        path {
            using(baseContext)
            +"jobs"
            +boundTo(AppRequest.Cancel::jobId)
        }
    }

    val appRequestBundle: KafkaCallDescriptionBundle<AppRequest> = listOf(startApp, cancelApp)
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
