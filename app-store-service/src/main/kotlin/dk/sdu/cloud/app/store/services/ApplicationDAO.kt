package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

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

    fun multiKeywordsearch(
        session: Session,
        user: SecurityPrincipal,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<ApplicationEntity>

    fun findAllByName(
        session: Session,
        user: SecurityPrincipal?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun findBySupportedFileExtension(
        session: Session,
        user: SecurityPrincipal,
        fileExtensions: Set<String>
    ): List<ApplicationWithExtension>

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
    ): ApplicationWithFavoriteAndTags

    fun listLatestVersion(
        session: Session,
        user: SecurityPrincipal?,

        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun isOwnerOfApplication(
        session: Session,
        user: SecurityPrincipal,
        name: String
    ): Boolean

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
        tags: List<String>
    )

    fun deleteTags(
        session: Session,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    )

    fun findTagsForApp(
        session: Session,
        applicationName: String
    ) : List<TagEntity>

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

    fun isPublic(
        session: Session,
        user: SecurityPrincipal,
        name: String,
        version: String
    ): Boolean

    fun setPublic(
        session: Session,
        user: SecurityPrincipal,
        name: String,
        version: String,
        public: Boolean
    )

    fun findAllByID(
        session: Session,
        user: SecurityPrincipal,
        embeddedNameAndVersionList: List<EmbeddedNameAndVersion>,
        paging: NormalizedPaginationRequest
    ): List<ApplicationEntity>

    fun findLatestByTool(
        session: Session,
        user: SecurityPrincipal,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application>

    fun preparePageForUser(
        session: Session,
        user: String?,
        page: Page<Application>
    ): Page<ApplicationWithFavoriteAndTags>
}
