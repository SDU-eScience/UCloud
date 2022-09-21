package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.*
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.projects.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayOutputStream

val UserCriteria.type: String
    get() = when (this) {
        is UserCriteria.Anyone -> UserCriteria.ANYONE_TYPE
        is UserCriteria.EmailDomain -> UserCriteria.EMAIL_TYPE
        is UserCriteria.WayfOrganization -> UserCriteria.WAYF_TYPE
    }

val UserCriteria.id: String?
    get() = when (this) {
        is UserCriteria.Anyone -> null
        is UserCriteria.EmailDomain -> domain
        is UserCriteria.WayfOrganization -> org
    }

class GrantSettingsService(
    private val db: DBContext,
) {
    suspend fun uploadRequestSettings(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UploadRequestSettingsRequest>
    ) {
        if (actorAndProject.project == null) throw RPCException("Settings only available in project context", HttpStatusCode.BadRequest)

        db.withSession(remapExceptions = true) { session ->
            request.items.forEach { req ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)

                        req.excludeRequestsFrom.split {
                            into("new_exclude_list") { excludeEntry ->
                                if (excludeEntry !is UserCriteria.EmailDomain) {
                                    throw RPCException(
                                        "Exclude list can only contain emails",
                                        HttpStatusCode.BadRequest
                                    )
                                }
                                excludeEntry.domain
                            }
                        }

                        req.allowRequestsFrom.split {
                            into("new_include_list_type") { it.type }
                            into("new_include_list_entity") { it.id }
                        }

                        req.automaticApproval.from.split {
                            into("auto_approve_list_type") { it.type }
                            into("auto_approve_list_entity") { it.id }
                        }

                        req.automaticApproval.maxResources.split {
                            into("auto_approve_category") { it.category }
                            into("auto_approve_provider") { it.provider }
                            into("auto_approve_quota") { null }
                            into("auto_approve_grant_giver") {actorAndProject.project}
                            into("auto_approve_credits") { it.balanceRequested }
                        }
                    },
                    """
                    select "grant".upload_request_settings(
                        :username, :project,
                        
                        :new_exclude_list,
                        
                        :new_include_list_type, :new_include_list_entity,
                        
                        :auto_approve_list_type, :auto_approve_list_entity, :auto_approve_category,
                        :auto_approve_provider, :auto_approve_credits, :auto_approve_quota, :auto_approve_grant_giver
                    )
                """
                )
            }
        }
    }

    suspend fun fetchSettings(
        actorAndProject: ActorAndProject,
        projectId: String,
    ): ProjectApplicationSettings {
        return db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("project", projectId)
                    setParameter("period_start", Time.now() / 1000)
                },
                """
                    select jsonb_build_object(
                        'projectId', :project::text,
                        'automaticApproval', jsonb_build_object(
                            'from', auto_approve_from,
                            'maxResources', auto_limit
                        ),
                        'allowRequestsFrom', allow_from,
                        'excludeRequestsFrom', exclude_from
                    )
                    from (
                        select
                            array_remove(array_agg(
                                case when exclude_entry.email_suffix is null then null else
                                jsonb_build_object(
                                    'type', 'email',
                                    'domain', exclude_entry.email_suffix
                                )
                                end
                            ), null) exclude_from,
                            
                            array_remove(array_agg(
                                case when allow_entry.type is null then null else
                                jsonb_build_object('type', allow_entry.type) || case
                                    when allow_entry.type = 'anyone' then '{}'::jsonb
                                    when allow_entry.type = 'email' then
                                        jsonb_build_object('domain', allow_entry.applicant_id)
                                    when allow_entry.type = 'wayf' then
                                        jsonb_build_object('org', allow_entry.applicant_id)
                                end
                                end
                            ), null) allow_from,
                            
                            array_remove(array_agg(
                                case when auto_users.type is null then null else 
                                jsonb_build_object('type', auto_users.type) || case
                                    when auto_users.type = 'anyone' then '{}'::jsonb
                                    when auto_users.type = 'email' then
                                        jsonb_build_object('domain', auto_users.applicant_id)
                                    when auto_users.type = 'wayf' then
                                        jsonb_build_object('org', auto_users.applicant_id)
                                end
                                end
                            ), null) auto_approve_from,
                            
                            array_remove(array_agg(
                                case when pc.category is null then null else 
                                jsonb_build_object(
                                    'category', pc.category,
                                    'provider', pc.provider,
                                    'creditsRequested', auto_limits.maximum_credits,
                                    'quotaRequested', auto_limits.maximum_quota_bytes,
                                    'grantGiver', auto_limits.grant_giver,
                                    'period', jsonb_build_object(
                                        'start', :period_start::bigint,
                                        'end', null
                                    )
                                )
                                end
                            ), null) auto_limit
                            
                        from
                            project.project_members pm left join
                            "grant".allow_applications_from allow_entry on
                                pm.project_id = allow_entry.project_id left join
                            "grant".exclude_applications_from exclude_entry on
                                pm.project_id = exclude_entry.project_id left join
                            "grant".automatic_approval_users auto_users on
                                pm.project_id = auto_users.project_id left join
                            "grant".automatic_approval_limits auto_limits on
                                pm.project_id = auto_limits.project_id left join
                            accounting.product_categories pc on
                                auto_limits.product_category = pc.id
                                
                        where
                            pm.project_id = :project::text and
                            (pm.role = 'ADMIN' or pm.role = 'PI') and
                            pm.username = :username::text
                    ) t 
                    where 
                        allow_from is not null or 
                        auto_approve_from is not null or 
                        exclude_from is not null or 
                        auto_limit is not null;
                """
            )
        }.rows.singleOrNull()?.let { defaultMapper.decodeFromString(it.getString(0)!!) }
            ?: throw RPCException(
                "Unable to fetch settings for this project. Are you an admin of the project?",
                HttpStatusCode.NotFound
            )
    }

    suspend fun setEnabledStatus(
        actorAndProject: ActorAndProject,
        requests: BulkRequest<SetEnabledStatusRequest>
    ) {
        when (val actor = actorAndProject.actor) {
            Actor.System -> {
                // Allow
            }
            is Actor.SystemOnBehalfOfUser -> {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
            is Actor.User -> {
                if (actor.principal.role !in Roles.PRIVILEGED) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                }
            }
        }
        db.withSession(remapExceptions = true) { session ->
            requests.items.forEach { req ->
                session.sendPreparedStatement(
                    {
                        setParameter("project_id", req.projectId)
                        setParameter("status", req.enabledStatus)
                    },
                    """
                        with deletion as (
                            delete from "grant".is_enabled
                            where project_id = :project_id
                        )
                        insert into "grant".is_enabled (project_id)
                        select :project_id
                        where :status
                    """
                )
            }
        }
    }

    suspend fun isEnabled(
        projectId: String
    ): Boolean {
        return db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("projectId", projectId)
                },
                """
                    select * 
                    from "grant".is_enabled 
                    where project_id = :projectId
                """
            ).rows.size > 0
        }
    }

    suspend fun browseAffiliationByResource(
        actorAndProject: ActorAndProject,
        request: GrantsBrowseAffiliationsByResourceRequest
    ): PageV2<ProjectWithTitle> {
        if (actorAndProject.project == null) {
            throw RPCException("Not in a project", HttpStatusCode.Forbidden)
        }
        val application = db.withSession {session ->
            session.sendPreparedStatement(
                {
                    setParameter("appId", request.applicationId)
                },
                """
                    select "grant".application_to_json(:appId) 
                """.trimIndent()
            ).rows.map {
                defaultMapper.decodeFromString<GrantApplication>(it.getString(0)!!)
            }.singleOrNull() ?: throw RPCException("Did not find application", HttpStatusCode.NotFound)
        }
        val requestedResources = application.currentRevision.document.allocationRequests
        val currentProjectResourceRequests = requestedResources.filter { it.grantGiver == actorAndProject.project }
        val grantGiversID = requestedResources.map { it.grantGiver }.toSet().toList()
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())

                        currentProjectResourceRequests.split {
                            into("providers") {it.provider}
                            into("category_ids") {it.category}
                        }
                        setParameter("resources_size", currentProjectResourceRequests.size)
                        setParameter("grant_givers", grantGiversID)
                    },
                    """
                        declare c cursor for
                        with
                            resources_requested as (
                                select 
                                    unnest(:providers::text[]) provider,
                                    unnest(:category_ids::text[]) category
                            ),
                            projects_with_resources as (
                                select 
                                    p.id, count(*) resources_match
                                from 
                                    project.projects p join
                                    accounting.wallet_owner wown on p.id = wown.project_id join
                                    accounting.wallets wall on wown.id = wall.owned_by join 
                                    accounting.product_categories pc on wall.category = pc.id join
                                    resources_requested rr on pc.category = rr.category and pc.provider = rr.provider
                                group by p.id
                            ),
                            preliminary_list as (
                                select
                                    allow_entry.project_id,
                                    requesting_user.id,
                                    requesting_user.email,
                                    requesting_user.org_id
                                from
                                    auth.principals requesting_user join
                                    "grant".allow_applications_from allow_entry on
                                        allow_entry.type = 'anyone' or

                                        (
                                            allow_entry.type = 'wayf' and
                                            allow_entry.applicant_id = requesting_user.org_id
                                        ) or

                                        (
                                            allow_entry.type = 'email' and
                                            requesting_user.email like '%@' || allow_entry.applicant_id
                                        )
                                where
                                    requesting_user.id = :username
                            ),
                            after_exclusion as (
                                select
                                    requesting_user.project_id
                                from
                                    preliminary_list requesting_user left join
                                    "grant".exclude_applications_from exclude_entry on 
                                        requesting_user.email like '%@' || exclude_entry.email_suffix and
                                        exclude_entry.project_id = requesting_user.project_id join 
                                    projects_with_resources pwr on 
                                        pwr.id = requesting_user.project_id and 
                                        pwr.resources_match = :resources_size
                                group by
                                    requesting_user.project_id
                                having
                                    count(email_suffix) = 0
                            )
                        select p.id, p.title
                        from after_exclusion res join project.projects p on 
                            res.project_id = p.id and 
                            p.id not in (select unnest(:grant_givers::text[]))
                        order by p.title
                    """
                )
            },
            mapper = { _, rows ->
                rows.mapNotNull {
                    ProjectWithTitle(it.getString(0)!!, it.getString(1)!!)
                }
            }
        )
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: WithPaginationRequestV2
    ): PageV2<ProjectWithTitle> {
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    { setParameter("username", actorAndProject.actor.safeUsername()) },
                    """
                        declare c cursor for
                        with
                            preliminary_list as (
                                select
                                    allow_entry.project_id,
                                    requesting_user.id,
                                    requesting_user.email,
                                    requesting_user.org_id
                                from
                                    auth.principals requesting_user join
                                    "grant".allow_applications_from allow_entry on
                                        allow_entry.type = 'anyone' or

                                        (
                                            allow_entry.type = 'wayf' and
                                            allow_entry.applicant_id = requesting_user.org_id
                                        ) or

                                        (
                                            allow_entry.type = 'email' and
                                            requesting_user.email like '%@' || allow_entry.applicant_id
                                        )
                                where
                                    requesting_user.id = :username
                            ),
                            after_exclusion as (
                                select
                                    requesting_user.project_id
                                from
                                    preliminary_list requesting_user left join
                                    "grant".exclude_applications_from exclude_entry on 
                                        requesting_user.email like '%@' || exclude_entry.email_suffix and
                                        exclude_entry.project_id = requesting_user.project_id
                                group by
                                    requesting_user.project_id
                                having
                                    count(email_suffix) = 0
                            )
                        select p.id, p.title
                        from after_exclusion res join project.projects p on res.project_id = p.id
                        order by p.title
                    """
                )
            },
            mapper = { _, rows -> rows.map { ProjectWithTitle(it.getString(0)!!, it.getString(1)!!) }}
        )
    }

    suspend fun fetchLogo(projectId: String): ByteArray? {
        return db.withSession(remapExceptions = true) { session ->
            session
                .sendPreparedStatement(
                    { setParameter("projectId", projectId) },
                    "select data from \"grant\".logos where project_id = :projectId"
                ).rows.singleOrNull()?.getAs<ByteArray>(0)
        }
    }

    suspend fun uploadDescription(
        actorAndProject: ActorAndProject,
        requests: BulkRequest<UploadDescriptionRequest>
    ) {
        db.withSession(remapExceptions = true) { session ->
            requests.items.forEach { req ->
                val success = session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project_id", req.projectId)
                        setParameter("description", req.description)
                    },
                    """
                    insert into "grant".descriptions (project_id, description)
                    select :project_id, :description
                    from project.project_members pm
                    where
                        pm.username = :username and
                        (pm.role = 'PI' or pm.role = 'ADMIN') and
                        pm.project_id = :project_id
                    on conflict (project_id) do update set
                        description = excluded.description
                """
                ).rowsAffected > 0

                if (!success) {
                    throw RPCException("Unable to update description.", HttpStatusCode.NotFound)
                }
            }
        }
    }

    suspend fun fetchDescription(projectId: String): String {
        return db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                { setParameter("projectId", projectId) },
                "select description from \"grant\".descriptions where project_id = :projectId"
            ).rows.singleOrNull()?.getString(0) ?: "No description"
        }
    }

    suspend fun uploadLogo(
        actorAndProject: ActorAndProject,
        projectId: String,
        streamLength: Long?,
        channel: ByteReadChannel,
    ) {
        if (streamLength == null || streamLength > LOGO_MAX_SIZE) {
            throw RPCException("Logo is too large", HttpStatusCode.BadRequest)
        }

        val imageBytes = ByteArrayOutputStream(streamLength.toInt()).let { stream ->
            channel.copyTo(stream)
            stream.toByteArray()
        }

        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("project_id", projectId)
                    setParameter("data", imageBytes)
                },
                """
                    insert into "grant".logos (project_id, data) 
                    select :project_id , :data
                    from
                        project.project_members pm
                    where
                        pm.username = :username and
                        (pm.role = 'PI' or pm.role = 'ADMIN') and
                        pm.project_id = :project_id
                    on conflict (project_id) do update set
                        data = excluded.data
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException("Unable to upload logo", HttpStatusCode.NotFound)
            }
        }
    }
}

const val LOGO_MAX_SIZE = 1024 * 512
