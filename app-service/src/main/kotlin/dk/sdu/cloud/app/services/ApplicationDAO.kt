package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ApplicationDAO<Session> {
    fun findAllByName(
        session: Session,
        user: String?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<Application>

    fun findByNameAndVersion(
        session: Session,
        user: String?,

        name: String,
        version: String
    ): Application

    fun listLatestVersion(
        session: Session,
        user: String?,

        paging: NormalizedPaginationRequest
    ): Page<Application>

    fun create(
        session: Session,
        user: String,
        description: NormalizedApplicationDescription,
        originalDocument: String = ""
    )

    fun updateDescription(
        session: Session,
        user: String,

        name: String,
        version: String,

        newDescription: String? = null,
        newAuthors: List<String>? = null
    )
}