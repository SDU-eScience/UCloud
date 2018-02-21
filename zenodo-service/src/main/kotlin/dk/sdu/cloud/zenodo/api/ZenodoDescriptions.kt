package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByIntId
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.netty.handler.codec.http.HttpMethod

data class ZenodoAccessRequest(val returnTo: String)
data class ZenodoAccessRedirectURL(val redirectTo: String)

data class ZenodoPublishRequest(val name: String, val filePaths: List<String>)
data class ZenodoPublishResponse(val publicationId: Int)

interface ZenodoIsConnected {
    val connected: Boolean
}

data class ZenodoErrorMessage(override val connected: Boolean, val why: String) : ZenodoIsConnected

data class ZenodoConnectedStatus(override val connected: Boolean) : ZenodoIsConnected

enum class ZenodoPublicationStatus {
    PENDING,
    UPLOADING,
    COMPLETE,
    FAILURE
}

data class ZenodoPublication(
    val id: Int,
    val name: String,
    val status: ZenodoPublicationStatus,
    val zenodoAction: String?,
    val createdAt: Long,
    val modifiedAt: Long
)

data class ZenodoUpload(
    val dataObject: String,
    val hasBeenTransmitted: Boolean,
    val updatedAt: Long
)

data class ZenodoPublicationWithFiles(
    val publication: ZenodoPublication,
    val uploads: List<ZenodoUpload>
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

    val publish = callDescription<ZenodoPublishRequest, ZenodoPublishResponse, ZenodoErrorMessage> {
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

    val findPublicationById = callDescription<FindByIntId, ZenodoPublicationWithFiles, ZenodoErrorMessage> {
        method = HttpMethod.GET
        prettyName = "findPublicationById"

        path {
            using(baseContext)
            +"publications"
            +boundTo(FindByIntId::id)
        }
    }
}