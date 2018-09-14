package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.jwtAuth
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.service.*
import io.ktor.http.HttpStatusCode

class ActivityService<DBSession>(
    private val dao: ActivityEventDao<DBSession>,
    private val cloud: AuthenticatedCloud
) {
    private val cloudContext: CloudContext = cloud.parent

    fun insert(
        session: DBSession,
        event: ActivityEvent
    ) {
        insertBatch(session, listOf(event))
    }

    fun insertBatch(
        session: DBSession,
        events: List<ActivityEvent>
    ) {
        dao.insertBatch(session, events)
    }

    suspend fun findEventsForPath(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        path: String,
        userAccessToken: String,
        causedBy: String? = null
    ): Page<ActivityEvent> {
        val serviceCloud = cloud.optionallyCausedBy(causedBy)

        val userCloud = AuthDescriptions.tokenExtension.call(
            TokenExtensionRequest(
                userAccessToken,
                listOf(FileDescriptions.stat.requiredAuthScope.toString()),
                expiresIn = 1000L * 60 * 1
            ),
            serviceCloud
        ).orThrow().asCloud(cloudContext, causedBy)

        val fileStat = FileDescriptions.stat.call(FindByPath(path), userCloud).orThrow()

        return findEventsForFileId(session, pagination, fileStat.fileId)
    }

    fun findEventsForFileId(
        session: DBSession,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent> {
        return dao.findByFileId(session, pagination, fileId)
    }
}

internal fun <T> RESTResponse<T, *>.orThrow(): T {
    if (this !is RESTResponse.Ok) {
        throw RPCException(rawResponseBody, HttpStatusCode.fromValue(status))
    }
    return result
}

internal fun AuthenticatedCloud.optionallyCausedBy(causedBy: String?): AuthenticatedCloud {
    return if (causedBy != null) withCausedBy(causedBy)
    else this
}

fun TokenExtensionResponse.asCloud(context: CloudContext, causedBy: String?): AuthenticatedCloud {
    return context.jwtAuth(this.accessToken).optionallyCausedBy(causedBy)
}