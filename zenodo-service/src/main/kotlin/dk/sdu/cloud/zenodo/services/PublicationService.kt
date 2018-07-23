package dk.sdu.cloud.zenodo.services

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.zenodo.api.ZenodoPublication
import dk.sdu.cloud.zenodo.api.ZenodoPublicationStatus
import dk.sdu.cloud.zenodo.api.ZenodoPublicationWithFiles
import io.ktor.http.HttpStatusCode

sealed class PublicationException(
    why: String,
    httpStatusCode: HttpStatusCode
) : RPCException(why, httpStatusCode) {
    class NotConnected : PublicationException("Not connected", HttpStatusCode.Unauthorized)
    class NotFound : PublicationException("Not found", HttpStatusCode.NotFound)
}

interface PublicationService<Session> {
    fun findById(
        session: Session,
        user: String,
        id: Long
    ): ZenodoPublicationWithFiles

    fun findForUser(
        session: Session,
        user: String,
        pagination: NormalizedPaginationRequest
    ): Page<ZenodoPublication>

    fun createUploadForFiles(
        session: Session,
        user: String,
        name: String,
        filePaths: Set<String>
    ): Long

    fun markUploadAsCompleteInPublication(
        session: Session,
        publicationId: Long,
        path: String
    )

    fun attachZenodoId(
        session: Session,
        publicationId: Long,
        zenodoId: String
    )

    fun updateStatusOf(
        session: Session,
        publicationId: Long,
        status: ZenodoPublicationStatus
    )
}

