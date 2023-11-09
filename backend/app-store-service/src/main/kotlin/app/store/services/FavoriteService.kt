package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.ApplicationSummaryWithFavorite
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*

class FavoriteService (
    private val db: AsyncDBSessionFactory,
    private val publicService: ApplicationPublicService,
    private val aclDao: AclAsyncDao,
    private val authenticatedClient: AuthenticatedClient
) {
    private suspend fun isFavorite(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        appName: String,
    ): Boolean {
        return 0L != ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.username)
                        setParameter("name", appName)
                    },
                    """
                        SELECT COUNT(*)
                        FROM favorited_by
                        WHERE (the_user = :user) AND
                            (application_name = :name)
                    """
                )
                .rows
                .singleOrNull()?.getLong(0) ?: 0
        }
    }


    suspend fun toggleFavorite(actorAndProject: ActorAndProject, appName: String) {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        db.withSession { session ->
            val isFavorite = isFavorite(session, actorAndProject, appName)

            if (isFavorite) {
                session.sendPreparedStatement(
                        {
                            setParameter("user", actorAndProject.actor.username)
                            setParameter("appname", appName)
                        },
                        """
                            DELETE FROM favorited_by
                            WHERE (the_user = :user) AND
                                (application_name = :appname)
                        """
                    )

            } else {
                val userHasPermission = internalHasPermission(
                    session,
                    actorAndProject,
                    projectGroups,
                    appName,
                    null,
                    ApplicationAccessRight.LAUNCH,
                    publicService,
                    aclDao
                )
                if (userHasPermission) {
                    session.sendPreparedStatement(
                        {
                            setParameter("name", appName)
                            setParameter("user", actorAndProject.actor.username)
                        },
                        """
                            insert into app_store.favorited_by
                                (the_user, application_name)
                            values
                                (:user, :name)
                        """
                    )
                } else {
                    throw RPCException("Unauthorized favorite request", HttpStatusCode.Unauthorized)
                }
            }
        }
    }

    suspend fun retrieveFavorites(
        actorAndProject: ActorAndProject,
        request: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return db.withSession { session ->
            val itemsInTotal =
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", actorAndProject.actor.username)
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

            val groups = projectGroups.ifEmpty {
                listOf("")
            }

            val itemsWithoutTags =
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", actorAndProject.actor.username)
                            setParameter("isAdmin", Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role))
                            setParameter("project", actorAndProject.project)
                            setParameter("groups", groups)
                            setParameter("limit", request.itemsPerPage)
                            setParameter("offset", request.offset)
                        },
                        """
                        SELECT DISTINCT ON (A.name) name, A.*
                        FROM favorited_by as F, applications as A
                        WHERE 
                            (F.the_user = :user) AND
                            (F.application_name = A.name) AND
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
                        ORDER BY A.name, A.created_at DESC
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
                        SELECT application_name, tag
                        FROM application_tags, tags
                        WHERE application_name IN (select unnest(:names::text[])) and tag_id = tags.id
                    """
                    )
                    .rows
                    .toList()

            val items = itemsWithoutTags.map { appSummary ->
                val allTagsForApplication =
                    allTags.filter { it.getString(0) == appSummary.metadata.name }
                        .mapNotNull { it.getString(1) }
                ApplicationSummaryWithFavorite(appSummary.metadata, true, allTagsForApplication)
            }

            Page(
                itemsInTotal.toInt(),
                request.itemsPerPage,
                request.page,
                items
            )
        }
    }

}
