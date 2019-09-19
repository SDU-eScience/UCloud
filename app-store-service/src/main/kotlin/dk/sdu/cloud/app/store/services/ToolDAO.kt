package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ToolDAO<Session> {
    fun findAllByName(
        session: Session,
        user: SecurityPrincipal?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<Tool>

    fun findByNameAndVersion(
        session: Session,
        user: SecurityPrincipal?,

        name: String,
        version: String
    ): Tool

    fun listLatestVersion(
        session: Session,
        user: SecurityPrincipal?,

        paging: NormalizedPaginationRequest
    ): Page<Tool>

    fun create(
        session: Session,
        user: SecurityPrincipal,
        description: NormalizedToolDescription,
        originalDocument: String = ""
    )

    fun updateDescription(
        session: Session,
        user: SecurityPrincipal,

        name: String,
        version: String,

        newDescription: String? = null,
        newAuthors: List<String>? = null
    )

    fun createLogo(
        session: Session,
        user: SecurityPrincipal,

        name: String,
        imageBytes: ByteArray
    )

    fun clearLogo(session: Session, user: SecurityPrincipal, name: String)

    fun fetchLogo(session: Session, name: String): ByteArray?
}
