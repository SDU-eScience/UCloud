package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.offset
import io.ktor.http.HttpStatusCode

class FavoriteAsyncDao(
    private val publicDao: ApplicationPublicAsyncDao,
    private val aclDao: AclAsyncDao
) {

    suspend fun toggleFavorite(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        appName: String,
        appVersion: String
    ) {
        ctx.withSession { session ->
            val foundApp =
                internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.BadApplication()
            val isFavorite = isFavorite(session, user, appName, appVersion)

            if (isFavorite) {

                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", user.username)
                            setParameter("appname", appName)
                            setParameter("appversion", appVersion)
                        },
                        """
                            DELETE FROM favorited_by
                            WHERE (the_user = :user) AND
                                (application_name = :appname) AND
                                (application_version = :appversion)
                        """
                    )

            } else {
                val userHasPermission = internalHasPermission(
                    session,
                    user,
                    project,
                    memberGroups,
                    foundApp.getField(ApplicationTable.idName),
                    foundApp.getField(ApplicationTable.idVersion),
                    ApplicationAccessRight.LAUNCH,
                    publicDao,
                    aclDao
                )
                if (userHasPermission) {
                    val id = session.allocateId()
                    session.insert(FavoriteApplicationTable) {
                        set(
                            FavoriteApplicationTable.applicationName,
                            foundApp.getField(ApplicationTable.idName)
                        )
                        set(
                            FavoriteApplicationTable.applicationVersion,
                            foundApp.getField(ApplicationTable.idVersion)
                        )
                        set(FavoriteApplicationTable.user, user.username)
                        set(FavoriteApplicationTable.id, id)
                    }
                } else {
                    throw RPCException("Unauthorized favorite request", HttpStatusCode.Unauthorized)
                }
            }
        }
    }

    private suspend fun isFavorite(
        ctx: DBContext,
        user: SecurityPrincipal,
        appName: String,
        appVersion: String
    ): Boolean {
        return 0L != ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", user.username)
                        setParameter("name", appName)
                        setParameter("version", appVersion)
                    },
                    """
                        SELECT COUNT(*)
                        FROM favorited_by
                        WHERE (the_user = :user) AND
                            (application_name = :name) AND
                            (application_version = :version)
                    """
                )
                .rows
                .singleOrNull()?.getLong(0) ?: 0
        }
    }

    suspend fun retrieveFavorites(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        return ctx.withSession { session ->
            val itemsInTotal =
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", user.username)
                        },
                        """
                        SELECT COUNT(*)
                        FROM favorited_by
                        WHERE the_user = :user
                    """
                    )
                    .rows
                    .singleOrNull()
                    ?.getLong(0) ?: 0

            val groups = if (memberGroups.isNotEmpty()) {
                memberGroups
            } else {
                listOf("")
            }

            val itemsWithoutTags =
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", user.username)
                            setParameter("isAdmin", Roles.PRIVILEGED.contains(user.role))
                            setParameter("project", project)
                            setParameter("groups", groups)
                            setParameter("limit", paging.itemsPerPage)
                            setParameter("offset", paging.offset)
                        },
                        """
                        SELECT A.*
                        FROM favorited_by as F, applications as A
                        WHERE 
                            (F.the_user = :user) AND
                            (F.application_name = A.name) AND
                            (F.application_version = A.version) AND 
                            (
                                (A.is_public = TRUE) OR
                                (
                                    cast(:project as text) is null AND :user IN (
                                        SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                                    )
                                ) OR
                                (
                                    cast(:project as text) is not null AND exists (
                                        SELECT P2.project_group FROM permissions AS P2 WHERE
                                            P2.application_name = A.name AND
                                            P2.project = cast(:project as text) AND
                                            P2.project_group IN (select unnest(:groups::text[]))
                                    )
                                ) OR
                                (                  
                                    :isAdmin
                                )
                            )
                        ORDER BY F.application_name
                        LIMIT :limit
                        OFFSET :offset
                    """
                    )
                    .rows
                    .map {
                        it.toApplicationSummary()
                    }


            val apps = itemsWithoutTags.map { it.metadata.name }
            val allTags =
                session
                    .sendPreparedStatement(
                        {
                            setParameter("names", apps)
                        },
                        """
                        SELECT *
                        FROM application_tags
                        WHERE application_name IN (select unnest(:names::text[]))
                    """
                    )
                    .rows
                    .toList()

            val items = itemsWithoutTags.map { appSummary ->
                val allTagsForApplication =
                    allTags.filter { it.getField(TagTable.applicationName) == appSummary.metadata.name }
                        .map { it.getField(TagTable.tag) }
                ApplicationSummaryWithFavorite(appSummary.metadata, true, allTagsForApplication)
            }

            Page(
                itemsInTotal.toInt(),
                paging.itemsPerPage,
                paging.page,
                items
            )
        }
    }

}
