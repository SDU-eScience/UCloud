package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ToolDAO<Session> {
    fun findAllByName(
        session: Session,
        user: String?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<Tool>

    fun findByNameAndVersion(
        session: Session,
        user: String?,

        name: String,
        version: String
    ): Tool

    fun listLatestVersion(
        session: Session,
        user: String?,

        paging: NormalizedPaginationRequest
    ): Page<Tool>

    fun create(
        session: Session,
        user: String,
        description: NormalizedToolDescription,
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
