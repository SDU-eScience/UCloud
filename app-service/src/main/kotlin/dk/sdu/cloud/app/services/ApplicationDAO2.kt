package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NewApplication
import dk.sdu.cloud.app.api.NewNormalizedApplicationDescription
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ApplicationDAO2<Session> {
    fun findAllByName(
        session: Session,
        user: String?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<NewApplication>

    fun findByNameAndVersion(
        session: Session,
        user: String?,

        name: String,
        version: String
    ): NewApplication

    fun listLatestVersion(
        session: Session,
        user: String?,

        paging: NormalizedPaginationRequest
    ): Page<NewApplication>

    fun create(
        session: Session,
        user: String,
        description: NewNormalizedApplicationDescription,
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