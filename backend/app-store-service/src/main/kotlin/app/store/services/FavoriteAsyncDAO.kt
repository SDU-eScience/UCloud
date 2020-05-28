package app.store.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.ApplicationException
import dk.sdu.cloud.app.store.services.ApplicationTable
import dk.sdu.cloud.app.store.services.FavoriteApplicationTable
import dk.sdu.cloud.app.store.services.FavoriteDAO
import dk.sdu.cloud.app.store.services.TagTable
import dk.sdu.cloud.app.store.services.toApplicationMetadata
import dk.sdu.cloud.app.store.services.toApplicationSummary
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.offset
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
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("user", user.username)
                        setParameter("application_name", appName)
                        setParameter("application_version", appVersion)
                    },
                    """
                        DELETE FROM favorite_by
                        WHERE (the_user = ?user) AND
                            (application_name = ?appname) AND
                            (application_version = ?appversion)
                    """.trimIndent()
                )
            }
        } else {
            val userHasPermission = ctx.withSession { session ->
                internalHasPermission(
                    session,
                    user,
                    project,
                    memberGroups,
                    foundApp.getField(ApplicationTable.idName),
                    foundApp.getField(ApplicationTable.idVersion),
                    ApplicationAccessRight.LAUNCH
                )
            }

            if (userHasPermission) {
                ctx.withSession { session ->
                    session.insert(FavoriteApplicationTable){
                        set(
                            FavoriteApplicationTable.applicationName,
                            foundApp.getField(ApplicationTable.idName)
                        )
                        set(
                            FavoriteApplicationTable.applicationVersion,
                            foundApp.getField(ApplicationTable.idVersion)
                        )
                        set(FavoriteApplicationTable.user, user.username)
                    }
                }
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
        return 0L != ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", user.username)
                    setParameter("name", appName)
                    setParameter("version", appVersion)
                },
                """
                    SELECT COUNT(*)
                    FROM favorite_by
                    WHERE (the_user = ?user) AND
                        (application_name = ?name) AND
                        (application_version = ?version)
                """.trimIndent()
            ).rows.singleOrNull()?.getLong(0) ?: 0
        }
    }

    override suspend fun retrieveFavorites(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val itemsInTotal = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", user.username)
                },
                """
                    SELECT COUNT(*)
                    FROM favorite_by
                    WHERE the_user = ?user
                """.trimIndent()
            ).rows.singleOrNull()?.getLong(0) ?: 0
        }

        val groups = if (memberGroups.isNotEmpty()) {
            memberGroups
        } else {
            listOf("")
        }

        val itemsWithoutTags = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", user.username)
                    setParameter("role", user.role.toString())
                    setParameter("project", project)
                    setParameter("groups", groups)
                    setParameter("privileged", Roles.PRIVILEDGED.toList())
                    setParameter("limit", paging.itemsPerPage)
                    setParameter("offset", paging.offset)
                },
                """
                SELECT A.*
                FROM favorited_by as F, applications as A
                WHERE 
                    (F.the_user = ?user) AND
                    (F.application_name = A.name) AND
                    (F.application_version = A.version) AND 
                    (
                        (A.is_public = TRUE) OR
                        (
                            cast(?project as text) is null AND ?user in (
                                SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                            )
                        ) OR
                        (
                            cast(:project as text) is not null AND exists (
                                SELECT P2.project_group FROM permissions AS P2 WHERE
                                    P2.application_name = A.name AND
                                    P2.project = cast(?project as text) AND
                                    P2.project_group IN (?groups)
                            )
                        ) OR
                        (                  
                            ?role IN (?privileged)
                        )
                    )
                ORDER BY F.application_name
                LIMIT ?limit
                OFFSET ?offset
            """.trimIndent()
            ).rows.map { it.toApplicationSummary() }.toList()
        }

        val apps = itemsWithoutTags.map { it.metadata.name }
        val allTags = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("names", apps)
                },
                """
                    SELECT *
                    FROM application_tags
                    WHERE application_name IN ?names
                """.trimIndent()
            ).rows.toList()
        }
        val items = itemsWithoutTags.map { appSummary ->
            val allTagsForApplication = allTags.filter { it.getField(TagTable.applicationName) == appSummary.metadata.name }.map { it.getField(TagTable.tag) }
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
