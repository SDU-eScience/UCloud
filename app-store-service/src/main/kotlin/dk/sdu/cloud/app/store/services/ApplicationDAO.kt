package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.api.ApplicationWithFavorite
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession

interface ApplicationDAO<Session> {
    fun toggleFavorite(
        session: Session,
        user: SecurityPrincipal,
        name: String,
        version: String
    )

    fun retrieveFavorites(
        session: Session,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun searchTags(
        session: Session,
        user: SecurityPrincipal,
        tags: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun search(
        session: Session,
        user: SecurityPrincipal,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun findAllByName(
        session: Session,
        user: SecurityPrincipal?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun findByNameAndVersion(
        session: Session,
        user: SecurityPrincipal?,

        name: String,
        version: String
    ): Application

    fun findByNameAndVersionForUser(
        session: Session,
        user: SecurityPrincipal,
        name: String,
        version: String
    ): ApplicationWithFavorite

    fun listLatestVersion(
        session: Session,
        user: SecurityPrincipal?,

        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun create(
        session: Session,
        user: SecurityPrincipal,
        description: Application,
        originalDocument: String = ""
    )

    fun createTags(
        session: Session,
        user: SecurityPrincipal,
        applicationName: String,
        applicationVersion: String,
        tags: List<String>
    )

    fun deleteTags(
        session: Session,
        user: SecurityPrincipal,
        applicationName: String,
        applicationVersion: String,
        tags: List<String>
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

    fun findLatestByTool(
        session: Session,
        user: SecurityPrincipal,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application>
}
