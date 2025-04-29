package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.Workflow
import dk.sdu.cloud.app.store.api.WorkflowLanguage
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.encodeToString
import dk.sdu.cloud.app.store.api.Workflows as Api

class Workflows(
    private val db: DBContext,
    private val idCardService: IdCardService,
) {
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: Api.Create.Request,
        ctx: DBContext = db,
    ): String {
        idCardService.fetchIdCard(actorAndProject)

        return ctx.withSession(remapExceptions = true) { session ->
            if (request.allowOverwrite) {
                session.sendPreparedStatement(
                    {
                        setParameter("workspace", actorAndProject.project ?: actorAndProject.actor.safeUsername())
                        setParameter("application_name", request.specification.applicationName)
                        setParameter("path", request.path)
                    },
                    """
                        delete from app_store.workflows
                        where
                            coalesce(project_id, created_by) = :workspace
                            and application_name = :application_name
                            and path = :path
                    """
                )
            }

            session.sendPreparedStatement(
                {
                    setParameter("created_by", actorAndProject.actor.safeUsername())
                    setParameter("project_id", actorAndProject.project)
                    setParameter("application_name", request.specification.applicationName)
                    setParameter("language", request.specification.language.name)
                    setParameter("is_open", true)
                    setParameter("path", request.path)
                    setParameter("init", request.specification.init)
                    setParameter("job", request.specification.job)
                    setParameter("inputs", defaultMapper.encodeToString(request.specification.inputs))
                    setParameter("readme", request.specification.readme)
                },
                """
                    insert into app_store.workflows
                        (created_by, project_id, application_name, language, is_open, path, init, job, inputs, readme) 
                    values
                        (:created_by, :project_id, :application_name, :language, :is_open, :path, :init, :job, :inputs, :readme) 
                    returning id
                """
            ).rows.single().getLong(0)!!.toString()
        }
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: Api.Browse.Request,
        ctx: DBContext = db,
    ): PageV2<Workflow> {
        val normalized = request.normalize()
        return browseBy(
            actorAndProject,
            normalized.itemsPerPage,
            normalized.next,
            filterApplicationName = request.filterApplicationName,
            ctx = ctx
        )
    }

    suspend fun browseBy(
        actorAndProject: ActorAndProject,
        itemsPerPage: Int = 250,
        nextToken: String? = null,
        filterById: Long? = null,
        filterApplicationName: String? = null,
        ctx: DBContext = db,
    ): PageV2<Workflow> {
        val idCard = idCardService.fetchIdCard(actorAndProject)
        var next = nextToken
        val result = ArrayList<Workflow>()

        ctx.withSession { session ->
            while (true) {
                val items = session.sendPreparedStatement(
                    {
                        setParameter("workspace", actorAndProject.project ?: actorAndProject.actor.safeUsername())
                        setParameter("next", next?.toLongOrNull())
                        setParameter("app_name", filterApplicationName)
                        setParameter("id", filterById)
                    },
                    """
                        with
                            workspace_flows as (
                                select
                                    w.id,
                                    provider.timestamp_to_unix(w.created_at)::int8 as created_at,
                                    w.created_by,
                                    w.project_id,
                                    w.application_name,
                                    w.language,
                                    w.init,
                                    w.job,
                                    w.inputs,
                                    w.path,
                                    w.is_open,
                                    w.readme
                                from
                                    app_store.workflows w
                                where
                                    coalesce(project_id, created_by) = :workspace
                                    and (
                                        :app_name::text is null
                                        or application_name = :app_name
                                    )
                                    and (
                                        :next::int8 is null
                                        or id > :next::int8
                                    )
                                    and (
                                        :id::int8 is null
                                        or id = :id
                                    )
                                order by id desc
                                limit ${itemsPerPage}
                            ),
                            workflow_permissions as (
                                select
                                    wf.id,
                                    to_jsonb(
                                        array_remove(
                                            array_agg(
                                                jsonb_build_object(
                                                    'group', p.group_id,
                                                    'permission', p.permission
                                                )
                                            ),
                                            null
                                        )
                                    ) as permissions
                                from
                                    workspace_flows wf
                                    join app_store.workflow_permissions p on wf.id = p.workflow_id
                                group by
                                    wf.id
                            )
                        select
                            wf.*,
                            coalesce(p.permissions, '[]'::jsonb) as permissions
                        from
                            workspace_flows wf
                            left join workflow_permissions p on wf.id = p.id
                        order by wf.id desc
                    """
                ).rows.map { row ->
                    Workflow(
                        row.getLong(0)!!.toString(),
                        row.getLong(1)!!,
                        Workflow.Owner(
                            row.getString(2)!!,
                            row.getString(3),
                        ),
                        Workflow.Specification(
                            row.getString(4)!!,
                            WorkflowLanguage.valueOf(row.getString(5)!!),
                            row.getString(6),
                            row.getString(7),
                            row.getString(8)
                                ?.let {
                                    runCatching {
                                        defaultMapper.decodeFromString<List<ApplicationParameter>>(it)
                                    }.getOrNull()
                                } ?: emptyList(),
                            row.getString(11),
                        ),
                        Workflow.Status(row.getString(9)!!),
                        Workflow.Permissions(
                            row.getBoolean(10)!!,
                            emptyList(),
                            row.getString(12)!!.let {
                                runCatching {
                                    defaultMapper.decodeFromString<List<Workflow.AclEntry>>(it)
                                }.getOrNull() ?: emptyList()
                            }
                        )
                    )
                }

                for (wf in items) {
                    val perms = HashSet<Workflow.Permission>()

                    if (wf.permissions.openToWorkspace) {
                        perms.add(Workflow.Permission.READ)
                    }

                    if (actorAndProject.actor.safeUsername() == wf.owner.createdBy) {
                        perms.add(Workflow.Permission.READ)
                        perms.add(Workflow.Permission.WRITE)
                        perms.add(Workflow.Permission.ADMIN)
                    }

                    if (idCard is IdCard.User) {
                        if (idCard.activeProject in idCard.adminOf) {
                            perms.add(Workflow.Permission.READ)
                            perms.add(Workflow.Permission.WRITE)
                            perms.add(Workflow.Permission.ADMIN)
                        }

                        if (perms.size != Workflow.Permission.entries.size) {
                            for (entry in wf.permissions.others) {
                                val gid = idCardService.lookupGidFromGroupId(entry.group)
                                if (gid != null && gid in idCard.groups) {
                                    perms.add(entry.permission)
                                }
                            }
                        }
                    }

                    wf.permissions.myself = perms.toList()
                    if (perms.isNotEmpty()) result.add(wf)
                }

                next = if (items.size < itemsPerPage) null else items.last().id
                if (next == null || result.isNotEmpty()) break
            }
        }

        return PageV2(
            itemsPerPage,
            result,
            next,
        )
    }

    suspend fun rename(
        actorAndProject: ActorAndProject,
        request: Api.Rename.Request,
        ctx: DBContext = db,
    ) {
        ctx.withSession(remapExceptions = true) { session ->
            val workflow = retrieve(actorAndProject, request.id, ctx = session)
            if (!workflow.permissions.myself.contains(Workflow.Permission.WRITE)) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            if (request.allowOverwrite) {
                session.sendPreparedStatement(
                    {
                        setParameter("workspace", actorAndProject.project ?: actorAndProject.actor.safeUsername())
                        setParameter("path", request.newPath)
                    },
                    """
                        delete from app_store.workflows
                        where
                            coalesce(project_id, created_by) = :workspace
                            and path = :path
                    """
                )
            }

            session.sendPreparedStatement(
                {
                    setParameter("id", workflow.id.toLong())
                    setParameter("new_path", request.id)
                },
                """
                    update app_store.workflows
                    set
                        path = :new_path,
                        modified_at = now()
                    where id = :id
                """
            )
        }
    }

    suspend fun delete(
        actorAndProject: ActorAndProject,
        id: String,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            val workflow = retrieve(actorAndProject, id, ctx = session)
            if (!workflow.permissions.myself.contains(Workflow.Permission.WRITE)) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            session.sendPreparedStatement(
                {
                    setParameter("id", workflow.id.toLong())
                },
                """
                    delete from app_store.workflows
                    where id = :id
                """
            )
        }
    }

    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: Api.UpdateAcl.Request,
        ctx: DBContext = db,
    ) {
        val idCard = idCardService.fetchIdCard(actorAndProject)
        if (idCard !is IdCard.User) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val filteredEntries = request.entries.filter { it.permission != Workflow.Permission.ADMIN }

        ctx.withSession { session ->
            val workflow = retrieve(actorAndProject, request.id, ctx = session)
            if (Workflow.Permission.ADMIN !in workflow.permissions.myself) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            session.sendPreparedStatement(
                {
                    setParameter("id", workflow.id.toLong())
                },
                """
                    delete from app_store.workflow_permissions
                    where workflow_id = :id
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("id", workflow.id.toLong())
                    setParameter("is_open", request.isOpenForWorkspace)
                },
                """
                    update app_store.workflows
                    set
                        is_open = :is_open,
                        modified_at = now()
                    where id = :id
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("id", workflow.id.toLong())
                    setParameter("group_ids", filteredEntries.map { it.group })
                    setParameter("permission", filteredEntries.map { it.permission.name })
                },
                """
                    with
                        entries as (
                            select
                                unnest(:group_ids::text[]) as group_id,
                                unnest(:permissions::text[]) as permission
                        )
                    insert into app_store.workflow_permissions (workflow_id, group_id, permission) 
                    select :id, group_id, permission
                    from entries
                """
            )
        }
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        ctx: DBContext = db,
    ): Workflow {
        val normId = id.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return browseBy(
            actorAndProject,
            itemsPerPage = 1,
            filterById = normId,
            ctx = ctx
        ).items.singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }
}
