package dk.sdu.cloud.accounting.services.projects.v2

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.util.PartialQuery
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class ProviderNotificationService(
    private val projectService: ProjectService,
    private val db: DBContext,
    private val providers: Providers<*>
) {
    init {
        projectService.addUpdateHandler { session, projects ->
            notifyChange(projects, session)
        }
    }

    suspend fun notifyChange(
        projectIds: Collection<String>,
        ctx: DBContext = db,
    ) {
        // NOTE(Dan): This function notifies relevant providers about a change in some project, which is relevant to
        // them. This function simply:
        //
        // 1. Collect all relevant providers per project (see ProjectService for more details)
        // 2. Write an entry into the database about the notification (ignoring duplicates)
        // 3. Notify every provider that a notification is waiting for them
        val notifiedProviders = ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("ids", projectIds.toSet().toList())
                },
                """
                    with
                        project_and_providers as (
                            select distinct p.id as project_id, pc.provider as provider_id
                            from
                                accounting.wallet_owner wo join
                                project.projects p on wo.project_id = p.id join
                                accounting.wallets w on wo.id = w.owned_by join
                                accounting.product_categories pc on w.category = pc.id join
                                accounting.wallet_allocations wa on w.id = wa.associated_wallet
                            where
                                p.id = some(:ids::text[])
                        )
                    insert into project.provider_notifications (provider_id, project_id) 
                    select provider_id, project_id
                    from project_and_providers
                    on conflict do nothing 
                    returning provider_id
                """
            ).rows.map { it.getString(0)!! }
        }

        // NOTE(Dan): We don't throw if an error occurs. Providers might simply be temporarily unavailable. Providers
        // can always query any pending notifications later if they wish. The API documentation also encourages that
        // providers will perform such a pull at start-up.
        notifiedProviders.forEach { providerId ->
            val comms = providers.prepareCommunication(providerId)
            ProjectNotificationsProvider(providerId).pullRequest.call(Unit, comms.client)
        }
    }

    suspend fun pullNotifications(
        actorAndProject: ActorAndProject,
        ctx: DBContext = db,
    ): BulkResponse<ProjectNotification> {
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)

        data class NotificationAndProject(val notificationId: String, val projectId: String)
        return ctx.withSession { session ->
            val relevantData = session.sendPreparedStatement(
                {
                    setParameter("provider_id", providerId)
                },
                """
                    select pn.id, pn.project_id
                    from project.provider_notifications pn
                    where
                        pn.provider_id = :provider_id
                    order by pn.created_at desc
                    limit 100
                """
            ).rows.map { NotificationAndProject(it.getLong(0)!!.toString(), it.getString(1)!!) }

            val relevantProjects = PartialQuery(
                {
                    setParameter("project_ids", relevantData.map { it.projectId })
                },
                """
                    with
                        the_projects as (
                            select p as project
                            from project.projects p
                            where id = some(:project_ids::text[])
                        )
                    select distinct p.project, null::text as role
                    from the_projects p
                    order by p.project
                """
            )

            val loadedProjects = projectService.loadProjects(
                actorAndProject.actor.safeUsername(),
                session,
                ProjectsRetrieveRequest( // TODO Replace with just flags
                    "NOT_USED",
                    includeMembers = true,
                    includeGroups = true,
                    includePath = true,
                    includeSettings = true,
                    includeArchived = true,
                ),
                relevantProjects
            )

            BulkResponse(
                loadedProjects.mapNotNull { p ->
                    val notificationId = relevantData.find { it.projectId == p.id }?.notificationId
                        ?: return@mapNotNull null
                    ProjectNotification(notificationId, p)
                }
            )
        }
    }

    suspend fun markAsRead(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = db,
    ) {
        val providerId = actorAndProject.actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)

        ctx.withSession { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("provider_id", providerId)
                    setParameter("ids", request.items.mapNotNull { it.id.toLongOrNull() })
                },
                """
                    delete from project.provider_notifications
                    where
                        id = some(:ids::bigint[]) and
                        provider_id = :provider_id
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException(
                    "Could not mark any of the notifications as read. Are you sure they are still valid? " +
                        "Try contacting support for assistance.",
                    HttpStatusCode.BadRequest
                )
            }
        }
    }
}
