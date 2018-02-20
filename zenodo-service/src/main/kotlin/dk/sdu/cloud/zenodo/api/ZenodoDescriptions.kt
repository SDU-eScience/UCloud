package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.netty.handler.codec.http.HttpMethod

data class ZenodoAccessRequest(val returnTo: String)
data class ZenodoAccessRedirectURL(val redirectTo: String)

data class ZenodoPublishRequest(val filePaths: List<String>)
data class ZenodoPublishResponse(val publishAt: String)

interface ZenodoIsConnected {
    val connected: Boolean
}

data class ZenodoErrorMessage(override val connected: Boolean, val why: String) : ZenodoIsConnected

data class ZenodoConnectedStatus(override val connected: Boolean) : ZenodoIsConnected

enum class ZenodoPublicationStatus {
    UPLOADING,
    COMPLETE,
    FAILURE
}

data class ZenodoPublication(
    val id: String,
    val status: ZenodoPublicationStatus,
    val zenodoAction: String?
)

data class ZenodoPublicationList(
    val inProgress: List<ZenodoPublication>,
    override val connected: Boolean = true
) : ZenodoIsConnected

object ZenodoDescriptions : RESTDescriptions(ZenodoServiceDescription) {
    private const val baseContext = "/api/zenodo"

    val requestAccess = callDescription<ZenodoAccessRequest, ZenodoAccessRedirectURL, CommonErrorMessage> {
        method = HttpMethod.POST
        prettyName = "requestAccess"

        path {
            using(baseContext)
            +"request"
        }

        params {
            +boundTo(ZenodoAccessRequest::returnTo)
        }
    }

    val publish = kafkaDescription<ZenodoPublishRequest> {
        method = HttpMethod.POST
        prettyName = "publish"

        path {
            using(baseContext)
            +"publish"
        }

        body { bindEntireRequestFromBody() }
    }

    val status = callDescription<Unit, ZenodoConnectedStatus, CommonErrorMessage> {
        method = HttpMethod.GET
        prettyName = "status"

        path {
            using(baseContext)
            +"status"
        }
    }

    val listPublications = callDescription<Unit, ZenodoPublicationList, ZenodoErrorMessage> {
        method = HttpMethod.GET
        prettyName = "listPublications"

        path {
            using(baseContext)
            +"publications"
        }
    }
}