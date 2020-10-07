package dk.sdu.cloud.app.kubernetes.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.service.TYPE_PROPERTY
import io.lettuce.core.MigrateArgs.Builder.auth
import dk.sdu.cloud.calls.*

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DemoRequest.Initialize::class, name = "initialize"),
    JsonSubTypes.Type(value = DemoRequest.Input::class, name = "input")
)
sealed class DemoRequest {
    data class Initialize(val jobId: String) : DemoRequest()
    data class Input(val streamId: String, val data: String) : DemoRequest()
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DemoResponse.Initialized::class, name = "initialize"),
    JsonSubTypes.Type(value = DemoResponse.Data::class, name = "data"),
    JsonSubTypes.Type(value = DemoResponse.Acknowledged::class, name = "ack")
)
sealed class DemoResponse {
    data class Initialized(val streamId: String) : DemoResponse()
    data class Data(val streamId: String, val data: String) : DemoResponse()
    class Acknowledged : DemoResponse()
}

object ShellDemo : CallDescriptionContainer("app.compute.kubernetes.shell") {
    const val baseContext = "/api/app/compute/kubernetes/shell"

    val demo = call<DemoRequest, DemoResponse, CommonErrorMessage>("demo") {
        auth {
            access = AccessRight.READ_WRITE
        }

        websocket(baseContext)
    }
}