package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext

interface FavoriteDAO {
    suspend fun toggleFavorite(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        appName: String,
        appVersion: String
    )

    suspend fun retrieveFavorites(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>
}

interface TagsDAO {
    suspend fun createTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    )

    suspend fun deleteTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        applicationName: String,
        tags: List<String>
    )

    suspend fun findTagsForApp(
        ctx: DBContext,
        applicationName: String
    ) : List<RowData>
}

interface ApplicationLogoDAO {
    suspend fun createLogo(
        ctx: DBContext,
        user: SecurityPrincipal,
        name: String,
        imageBytes: ByteArray
    )

    suspend fun clearLogo(ctx: DBContext, user: SecurityPrincipal, name: String)

    suspend fun fetchLogo(ctx: DBContext, name: String): ByteArray?
}

interface PublicDAO {
    suspend fun isPublic(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String
    ): Boolean

    suspend fun setPublic(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String,
        public: Boolean
    )
}

interface SearchDAO {
    suspend fun searchByTags(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        tags: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    suspend fun search(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    suspend fun multiKeywordsearch(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        keywords: List<String>,
        paging: NormalizedPaginationRequest
    ): List<RowData>

}

interface ApplicationDAO {

    suspend fun findAllByName(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    suspend fun findBySupportedFileExtension(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        fileExtensions: Set<String>
    ): List<ApplicationWithExtension>

    suspend fun findByNameAndVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ): Application

    suspend fun findByNameAndVersionForUser(
        ctx: DBContext,
        user: SecurityPrincipal,
        currentProject: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    ): ApplicationWithFavoriteAndTags

    suspend fun listLatestVersion(
        ctx: DBContext,
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite>

    suspend fun isOwnerOfApplication(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String
    ): Boolean

    suspend fun create(
        ctx: DBContext,
        user: SecurityPrincipal,
        description: Application,
        originalDocument: String = ""
    )

    suspend fun delete(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        appName: String,
        appVersion: String
    )

    suspend fun updateDescription(
        ctx: DBContext,
        user: SecurityPrincipal,

        appName: String,
        appVersion: String,

        newDescription: String? = null,
        newAuthors: List<String>? = null
    )

    suspend fun findAllByID(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        embeddedNameAndVersionList: List<EmbeddedNameAndVersion>,
        paging: NormalizedPaginationRequest
    ): List<RowData>

    suspend fun findLatestByTool(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        projectGroups: List<String>,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application>

    suspend fun preparePageForUser(
        ctx: DBContext,
        user: String?,
        page: Page<Application>
    ): Page<ApplicationWithFavoriteAndTags>
}
