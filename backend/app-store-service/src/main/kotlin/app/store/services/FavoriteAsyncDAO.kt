package app.store.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.ApplicationException
import dk.sdu.cloud.app.store.services.FavoriteDAO
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode

class FavoriteAsyncDAO() : FavoriteDAO {

    override suspend fun toggleFavorite(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        appName: String,
        appVersion: String
    ) {
        val foundApp =
            ctx.withSession { session ->
                internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.BadApplication()
            }
        val isFavorite = ctx.withSession { session -> isFavorite(session, user, appName, appVersion) }

        if (isFavorite) {
            val query = session.createQuery(
                """
                delete from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.applicationName = :name
                    and A.applicationVersion = :version
                """.trimIndent()
            ).setParameter("user", user.username)
                .setParameter("name", appName)
                .setParameter("version", appVersion)

            query.executeUpdate()
        } else {
            val userHasPermission = internalHasPermission(
                session,
                user,
                project,
                memberGroups,
                foundApp.id.name,
                foundApp.id.version,
                ApplicationAccessRight.LAUNCH
            )

            if (userHasPermission) {
                session.save(
                    FavoriteApplicationEntity(
                        foundApp.id.name,
                        foundApp.id.version,
                        user.username
                    )
                )
            } else {
                throw RPCException("Unauthorized favorite request", HttpStatusCode.Unauthorized)
            }
        }
    }

    private suspend fun isFavorite(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String
    ): Boolean {
        return 0L != session.typedQuery<Long>(
            """
                select count (A.applicationName)
                from FavoriteApplicationEntity as A
                where A.user = :user
                    and A.applicationName = :name
                    and A.applicationVersion= :version
            """.trimIndent()
        ).setParameter("user", user.username)
            .setParameter("name", appName)
            .setParameter("version", appVersion)
            .uniqueResult()
    }

    override suspend fun retrieveFavorites(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val itemsInTotal = session.createCriteriaBuilder<Long, FavoriteApplicationEntity>().run {
            criteria.where(entity[FavoriteApplicationEntity::user] equal user.username)
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult()

        val groups = if (memberGroups.isNotEmpty()) {
            memberGroups
        } else {
            listOf("")
        }

        val itemsWithoutTags = session.createNativeQuery<ApplicationEntity>(
            """
            select A.*
            from app_store.favorited_by as F, app_store.applications as A
            where
                F.the_user = :user and
                F.application_name = A.name and
                F.application_version = A.version and (
                    (
                        A.is_public = TRUE
                    ) or (
                        cast(:project as text) is null and :user in (
                            select P1.username from app_store.permissions as P1 where P1.application_name = A.name
                        )
                    ) or (
                        cast(:project as text) is not null and exists (
                            select P2.project_group from app_store.permissions as P2 where
                                P2.application_name = A.name and
                                    P2.project = cast(:project as text) and
                                    P2.project_group in (:groups)
                        )
                    ) or (
                        :role in (:privileged)
                    )
                )
                order by F.application_name 
        """.trimIndent(), ApplicationEntity::class.java
        )
            .setParameter("user", user.username)
            .setParameter("role", user.role)
            .setParameter("project", project)
            .setParameterList("groups", groups)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .paginatedList(paging)
            .asSequence()
            .map { it.toModel() }
            .toList()

        val apps = itemsWithoutTags.map { it.metadata.name }
        val allTags = session
            .criteria<TagEntity> { entity[TagEntity::applicationName] isInCollection (apps) }
            .resultList
        val items = itemsWithoutTags.map { appSummary ->
            val allTagsForApplication = allTags.filter { it.applicationName == appSummary.metadata.name }.map { it.tag }
            ApplicationSummaryWithFavorite(appSummary.metadata, true, allTagsForApplication)
        }

        return Page(
            itemsInTotal.toInt(),
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

}
