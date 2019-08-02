package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.api.ApplicationWithFavorite
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface ApplicationDAO<Session> {
    fun toggleFavorite(
        session: Session,
        user: String,
        name: String,
        version: String
    )

    fun retrieveFavorites(
        session: Session,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun searchTags(
        session: Session,
        user: String,
        tags: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun search(
        session: Session,
        user: String,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun findAllByName(
        session: Session,
        user: String?,

        name: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun findByNameAndVersion(
        session: Session,
        user: String?,

        name: String,
        version: String
    ): Application

    fun findByNameAndVersionForUser(
        session: Session,
        user: String,
        name: String,
        version: String
    ): ApplicationWithFavorite

    fun listLatestVersion(
        session: Session,
        user: String?,

        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    fun create(
        session: Session,
        user: String,
        description: Application,
        originalDocument: String = ""
    )

    fun createTags(
        session: Session,
        tags: List<String>,
        applicationName: String,
        applicationVersion: String,
        user: String
    )

    fun deleteTags(
        session: Session,
        tags: List<String>,
        applicationName: String,
        applicationVersion: String,
        user: String
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
