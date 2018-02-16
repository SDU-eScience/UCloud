package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.netty.handler.codec.http.HttpMethod

data class ZenodoAccessRequest(val returnTo: String)
data class ZenodoAccessRedirectURL(val redirectTo: String)

data class ZenodoPublishRequest(val filePaths: List<String>)
data class ZenodoPublishResponse(val publishAt: String)

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
}