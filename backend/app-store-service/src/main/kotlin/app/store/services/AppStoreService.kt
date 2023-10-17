package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.api.CreateGroupResponse
import dk.sdu.cloud.app.store.api.Project
import dk.sdu.cloud.app.store.api.ProjectGroup
import dk.sdu.cloud.app.store.services.acl.*
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.encodeToString
import java.time.LocalDateTime

class AppStoreService(
    private val db: DBContext,
    private val authenticatedClient: AuthenticatedClient,
    private val publicService: ApplicationPublicService,
    private val toolDao: ToolAsyncDao,
    private val aclDao: AclAsyncDao,
    private val elasticDao: ElasticDao?,
    private val appEventProducer: AppEventProducer?
) {
    suspend fun findByNameAndVersion(
        actorAndProject: ActorAndProject,
        appName: String,
        appVersion: String
    ): ApplicationWithFavoriteAndTags {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return db.withSession { session ->
            val hasPermission = internalHasPermission(
                session,
                actorAndProject,
                projectGroups,
                appName,
                appVersion,
                ApplicationAccessRight.LAUNCH,
                publicService,
                aclDao
            )

            if (!hasPermission) throw ApplicationException.NotFound()

            val application = internalByNameAndVersion(session, appName, appVersion)?.toApplicationWithInvocation()
                ?: throw ApplicationException.NotFound()

            val page = preparePageForUser(
                session,
                actorAndProject.actor.username,
                Page(1, 1, 0, listOf(application))
            ).items.first()

            val toolRef = page.invocation.tool
            val tool = toolDao.findByNameAndVersion(session, (actorAndProject.actor as? Actor.User)?.principal, toolRef.name, toolRef.version)

            page.copy(
                invocation = page.invocation.copy(
                    tool = ToolReference(
                        toolRef.name,
                        toolRef.version,
                        tool
                    )
                )
            )
        }
    }

    suspend fun hasPermission(
        actorAndProject: ActorAndProject,
        appName: String,
        appVersion: String,
        permissions: Set<ApplicationAccessRight>
    ): Boolean {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return db.withSession { session ->
            publicService.isPublic(session, actorAndProject, appName, appVersion) ||
                aclDao.hasPermission(
                    session,
                    actorAndProject,
                    projectGroups,
                    appName,
                    permissions
                )
        }
    }

    suspend fun listAcl(
        actorAndProject: ActorAndProject,
        applicationName: String
    ): List<DetailedEntityWithPermission> {
        if ((actorAndProject.actor as? Actor.User)?.principal?.role != Role.ADMIN) throw RPCException(
            "Unable to access application permissions",
            HttpStatusCode.Unauthorized
        )
        return db.withSession { session ->
            aclDao.listAcl(
                session,
                applicationName
            ).map { accessEntity ->
                val projectAndGroupLookup =
                    if (!accessEntity.entity.project.isNullOrBlank() && !accessEntity.entity.group.isNullOrBlank()) {
                        ProjectGroups.lookupProjectAndGroup.call(
                            LookupProjectAndGroupRequest(accessEntity.entity.project!!, accessEntity.entity.group!!),
                            authenticatedClient
                        ).orRethrowAs {
                            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                        }
                    } else {
                        null
                    }

                DetailedEntityWithPermission(
                    if (projectAndGroupLookup != null) {
                        DetailedAccessEntity(
                            null,
                            Project(
                                projectAndGroupLookup.project.id,
                                projectAndGroupLookup.project.title
                            ),
                            ProjectGroup(
                                projectAndGroupLookup.group.id,
                                projectAndGroupLookup.group.title
                            )
                        )
                    } else {
                        DetailedAccessEntity(
                            accessEntity.entity.user,
                            null,
                            null
                        )
                    },
                    accessEntity.permission
                )
            }
        }
    }

    suspend fun updatePermissions(
        actorAndProject: ActorAndProject,
        applicationName: String,
        changes: List<ACLEntryRequest>
    ) {
        return db.withSession { session ->
            verifyAppUpdatePermission(actorAndProject, session, applicationName)

            changes.forEach { change ->
                if (!change.revoke) {
                    updatePermissionsWithSession(session, applicationName, change.entity, change.rights)
                } else {
                    revokePermissionWithSession(session, applicationName, change.entity)
                }
            }
        }
    }

    private suspend fun updatePermissionsWithSession(
        session: DBContext,
        applicationName: String,
        entity: AccessEntity,
        permissions: ApplicationAccessRight
    ) {
        if (!entity.user.isNullOrBlank()) {
            log.debug("Verifying that user exists")

            val lookup = UserDescriptions.lookupUsers.call(
                LookupUsersRequest(listOf(entity.user!!)),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            if (lookup.results[entity.user!!] == null) throw RPCException.fromStatusCode(
                HttpStatusCode.BadRequest,
                "The user does not exist"
            )

            if (lookup.results[entity.user!!]?.role == Role.SERVICE) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "The user does not exist")
            }
            aclDao.updatePermissions(session, entity, applicationName, permissions)
        } else if (!entity.project.isNullOrBlank() && !entity.group.isNullOrBlank()) {
            log.debug("Verifying that project exists")

            val projectLookup = Projects.lookupByPath.call(
                LookupByTitleRequest(entity.project!!),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            log.debug("Verifying that project group exists")

            val groupLookup = ProjectGroups.lookupByTitle.call(
                LookupByGroupTitleRequest(projectLookup.id, entity.group!!),
                authenticatedClient
            ).orRethrowAs {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "The project group does not exist"
                )
            }

            val entityWithProjectId = AccessEntity(
                null,
                projectLookup.id,
                groupLookup.groupId
            )

            aclDao.updatePermissions(session, entityWithProjectId, applicationName, permissions)
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Neither user or project group defined")
        }
    }

    private suspend fun revokePermissionWithSession(
        session: DBContext,
        applicationName: String,
        entity: AccessEntity
    ) {
        aclDao.revokePermission(session, entity, applicationName)
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun findBySupportedFileExtension(
        actorAndProject: ActorAndProject,
        request: NormalizedPaginationRequestV2,
        project: String?,
        files: List<String>
    ): PageV2<ApplicationWithExtension> {
        val extensions = files.flatMap { file ->
            if (file.contains(".")) {
                listOf("." + file.substringAfterLast('.'))
            } else {
                buildList {
                    val name = file.substringAfterLast('/')
                    add(name.removeSuffix("/"))

                    if (file.endsWith("/")) {
                        add("$name/")
                        add("/")
                    }
                }
            }
        }.toSet()

        val projectGroups = if (project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return db.paginateV2(
            actorAndProject.actor,
            request,
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", actorAndProject.actor.username)
                            setParameter("ext", extensions.toList())
                            setParameter("current_project", project)
                            setParameter("project_groups", projectGroups)
                        },
                        """
                            declare c cursor for
                            with
                                project_info as (
                                    select :current_project current_project,
                                           unnest(:project_groups::text[]) as project_group
                                ),
                                most_recent_applications as (
                                    select apps_with_rno.*
                                    from (
                                        select
                                            a.*,
                                            row_number() over (
                                                partition by name
                                                order by created_at desc
                                            ) as rno
                                        from
                                            applications a left join
                                            permissions p on a.name = p.application_name left join
                                            project_info pinfo
                                                on p.project = pinfo.current_project and p.project_group = pinfo.project_group
                                        where
                                            a.is_public = true or
                                            p.permission is distinct from null
                                    ) apps_with_rno
                                    where rno <= 1
                                )
                            select a.*
                            from
                                most_recent_applications a
                            where
                                (a.application -> 'fileExtensions' ??| :ext::text[])

                            order by a.title
                        """,
                    )
            },
            mapper = { _, rows ->
                rows.filter { rowData ->
                    defaultMapper.decodeFromString<ApplicationInvocationDescription>(
                        rowData.getString("application")!!
                    ).parameters.all { it.optional }
                }
                    .map {
                        ApplicationWithExtension(
                            it.toApplicationMetadata(),
                            defaultMapper.decodeFromString<ApplicationInvocationDescription>(
                                it.getString("application")!!
                            ).fileExtensions
                        )
                    }
            }
        )
    }

    suspend fun getAllApps(user: SecurityPrincipal): List<RowData> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("query", "")
                        setParameter("isAdmin", Roles.PRIVILEGED.contains(user.role))
                        setParameter("user", user.username)
                    },
                    """
                    SELECT *
                    FROM applications as A
                    WHERE LOWER(title) like '%' || :query || '%' AND
                    (
                        is_public = TRUE OR 
                        :user IN (
                            SELECT P.username FROM permissions AS P WHERE P.application_name = A.name
                        ) OR :isAdmin
                    )
                    ORDER BY A.title
                """
                )
                .rows
                .toList()
        }
    }

    suspend fun listLatestVersion(
        user: SecurityPrincipal?,
        currentProject: String?,
        projectGroups: List<String>,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (projectGroups.isEmpty()) {
            listOf("")
        } else {
            projectGroups
        }

        val cleanRole = user?.role ?: Role.UNKNOWN

        return db.withSession { session ->
            val items = session.sendPreparedStatement(
                {
                    setParameter("project", currentProject)
                    setParameter("groups", groups)
                    setParameter("isAdmin", Roles.PRIVILEGED.contains(cleanRole))
                    setParameter("user", user?.username ?: "")
                },
                """
                SELECT A.*
                FROM applications AS A WHERE (A.created_at) IN (
                    SELECT MAX(created_at)
                    FROM applications as B
                    WHERE A.name = B.name AND (
                        (
                            B.is_public = TRUE
                        ) or (
                            cast(:project as text) is null and :user in (
                                SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = B.name
                            )
                        ) or (
                            cast(:project as text) is not null AND exists (
                                SELECT P2.project_group FROM permissions AS P2 WHERE
                                    P2.application_name = B.name AND
                                    P2.project = cast(:project as text) AND
                                    P2.project_group IN ( select unnest(:groups::text[]))
                            )
                        ) or (
                            :isAdmin
                        )
                    )
                    GROUP BY B.name
                )
                ORDER BY A.name
                """
            )
                .rows

            db.withSession { session ->
                preparePageForUser(
                    session,
                    user?.username,
                    Page(
                        items.size,
                        paging.itemsPerPage,
                        paging.page,
                        items.map { it.toApplicationWithInvocation() }
                    )
                ).mapItems { it.withoutInvocation() }
            }
        }
    }

    suspend fun findByName(
        actorAndProject: ActorAndProject,
        appName: String,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationWithFavoriteAndTags> {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(
                actorAndProject,
                authenticatedClient
            )
        }

        val groups = projectGroups.ifEmpty {
            listOf("")
        }

        val result = db.withSession { session ->
            val page = session.sendPreparedStatement(
                {
                    setParameter("name", appName)
                    setParameter("project", actorAndProject.project)
                    setParameter("groups", groups)
                    setParameter(
                        "role",
                        ((actorAndProject.actor as? Actor.User)?.principal?.role ?: Role.UNKNOWN).toString()
                    )
                    setParameter("privileged", Roles.PRIVILEGED.toList())
                    setParameter("user", actorAndProject.actor.username)
                },
                """
                    SELECT A.*, ag.id as group_id, ag.title as group_title, ag.description as group_description FROM applications AS A
                    JOIN app_store.application_groups ag on A.group_id = ag.id
                    WHERE A.name = :name AND (
                        (
                            A.is_public = TRUE
                        ) OR (
                            cast(:project as text) is null AND :user IN (
                                SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                            )
                        ) OR (
                            cast(:project as text) is not null and exists (
                                SELECT P2.project_group FROM permissions AS P2 WHERE
                                    P2.application_name = A.name AND
                                    P2.project = cast(:project as text) AND
                                    P2.project_group in (select unnest(:groups::text[]))
                            )
                        ) OR (
                            :role in (select unnest(:privileged::text[]))
                        ) 
                    )
                    ORDER BY A.created_at DESC
                """
            ).rows.paginate(paging).mapItems {
                it.toApplicationWithInvocation()
            }

            preparePageForUser(
                session,
                actorAndProject.actor.username,
                page
            )
        }

        return result.mapItems { it }
    }

    suspend fun listAll(
        actorAndProject: ActorAndProject,
        paging: NormalizedPaginationRequest
    ): Page<ApplicationSummaryWithFavorite> {
        val groups = if (actorAndProject.project.isNullOrBlank()) {
            listOf("")
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        val cleanRole = (actorAndProject.actor as? Actor.User)?.principal?.role ?: Role.UNKNOWN

        return db.withSession { session ->
            val items = session.sendPreparedStatement(
                {
                    setParameter("project", actorAndProject.project)
                    setParameter("groups", groups)
                    setParameter("isAdmin", Roles.PRIVILEGED.contains(cleanRole))
                    setParameter("user", actorAndProject.actor.username)
                },
                """
            SELECT A.*
            FROM applications AS A WHERE (A.created_at) IN (
                SELECT MAX(created_at)
                FROM applications as B
                WHERE A.name = B.name AND (
                    (
                        B.is_public = TRUE
                    ) or (
                        cast(:project as text) is null and :user in (
                            SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = B.name
                        )
                    ) or (
                        cast(:project as text) is not null AND exists (
                            SELECT P2.project_group FROM permissions AS P2 WHERE
                                P2.application_name = B.name AND
                                P2.project = cast(:project as text) AND
                                P2.project_group IN ( select unnest(:groups::text[]))
                        )
                    ) or (
                        :isAdmin
                    )
                )
                GROUP BY B.name
            )
            ORDER BY A.name
            """
            ).rows

            preparePageForUser(
                session,
                actorAndProject.actor.username,
                Page(
                    items.size,
                    paging.itemsPerPage,
                    paging.page,
                    items.map { it.toApplicationWithInvocation() }
                )
            ).mapItems { it.withoutInvocation() }
        }
    }

    suspend fun browseSections(
        actorAndProject: ActorAndProject,
        pageType: AppStorePageType
    ): AppStoreSectionsResponse {
        val groups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return AppStoreSectionsResponse(
            db.withSession { session ->
                val sections = mutableMapOf<Int, String>()
                val items = mutableMapOf<Int, ArrayList<ApplicationGroup>>()
                val featured = mutableMapOf<Int, ArrayList<ApplicationGroup>>()

                session.sendPreparedStatement(
                    {
                        setParameter("page", pageType.name)
                    },
                    """
                        select
                            g.id,
                            g.title as title,
                            g.description,
                            s.id as section_id,
                            s.title as section_title,
                            g.default_name,
                            g.default_version
                        from
                            app_store.sections s
                            left join app_store.section_tags st on s.id = st.section_id
                            left join app_store.group_tags gt on gt.tag_id = st.tag_id
                            left join app_store.application_groups g on gt.group_id = g.id
                        where
                            page = :page
                        order by s.order_index
                    """
                ).rows.forEach { row ->
                    val groupId = row.getInt(0) ?: return@forEach
                    val groupTitle = row.getString(1) ?: return@forEach
                    val description = row.getString(2)
                    val sectionId = row.getInt(3) ?: return@forEach
                    val sectionTitle = row.getString(4) ?: return@forEach

                    sections[sectionId] = sectionTitle
                    items
                        .getOrPut(sectionId) { ArrayList() }
                        .add(ApplicationGroup(groupId, groupTitle, description, row.defaultApplication()))
                }

                session.sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.username)
                        setParameter("is_admin", Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role))
                        setParameter("project", actorAndProject.project)
                        setParameter("groups", groups)
                        setParameter("page", pageType.name)
                    },
                    """
                        with cte as (
                            select
                                g.id,
                                s.id as section_id,
                                s.title as section_title,
                                g.title as title,
                                g.description,
                                g.default_name,
                                g.default_version,
                                s.order_index as s_index,
                                f.order_index as f_index
                            from app_store.sections s
                            join app_store.section_featured_items f on f.section_id = s.id
                            join app_store.application_groups g on g.id = f.group_id and (
                                :is_admin or 0 < (
                                    select count(a.name)
                                    from app_store.applications a
                                    where a.group_id = g.id and (
                                        a.is_public or (
                                            cast(:project as text) is null and :user in (
                                                select p.username from app_store.permissions p where p.application_name = a.name
                                            )
                                        ) or (
                                            cast(:project as text) is not null and exists (
                                                select p.project_group from app_store.permissions p where
                                                    p.application_name = a.name and
                                                    p.project = cast(:project as text) and
                                                    p.project_group in (select unnest(:groups::text[]))
                                            )
                                        )
                                    )
                                )
                            )
                            where page = :page
                        )
                        select * from cte order by s_index, f_index;
                    """
                ).rows.forEach { row ->
                    val sectionId = row.getInt("section_id")!!
                    val sectionTitle = row.getString("section_title")!!
                    val groupId = row.getInt("id")!!
                    val groupTitle = row.getString("title")!!
                    val description = row.getString("description")

                    sections[sectionId] = sectionTitle
                    featured
                        .getOrPut(sectionId) { ArrayList() }
                        .add(ApplicationGroup(groupId, groupTitle, description, row.defaultApplication()))

                    items[sectionId]?.removeIf { it.id == groupId }
                }

                sections.map { section ->
                    AppStoreSection(section.key, section.value, featured[section.key] ?: emptyList(), items[section.key] ?: emptyList())
                }
            }
        )
    }

    suspend fun createGroup(actorAndProject: ActorAndProject, title: String): CreateGroupResponse {
        return db.withSession { session ->
            val existing = session.sendPreparedStatement(
                {
                    setParameter("title", title.lowercase())
                },
                """
                    select from app_store.application_groups where lower(title) = :title  
                """
            ).rows.size

            if (existing > 0) {
                throw RPCException("Group with name $title already exists", HttpStatusCode.BadRequest)
            }

            val id = session.sendPreparedStatement(
                {
                    setParameter("title", title)
                },
                """
                    insert into app_store.application_groups (title) values (:title)
                    returning id
                """
            ).rows.first().getInt("id")
                ?: throw RPCException("Failed to create group", HttpStatusCode.BadRequest)

            CreateGroupResponse(id)
        }
    }

    suspend fun deleteGroup(actorAndProject: ActorAndProject, id: Int) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                },
                """
                   update app_store.applications set group_id = null where group_id = :id 
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                },
                """
                   delete from app_store.application_groups where id = :id 
                """
            )
        }
    }

    suspend fun updateGroup(
        actorAndProject: ActorAndProject,
        id: Int,
        title: String,
        logo: ByteArray? = null,
        description: String? = null,
        defaultApplication: NameAndVersion? = null
    ) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("title", title)
                    setParameter("logo", logo)
                    setParameter("description", description)
                    setParameter("default_name", defaultApplication?.name)
                    setParameter("default_version", defaultApplication?.version)
                },
                """
                    update app_store.application_groups
                    set
                        title = :title,
                        logo = :logo,
                        description = :description,
                        default_name = :default_name,
                        default_version = :default_version 
                    where id = :id 
                """
            )
        }
    }

    suspend fun setGroup(actorAndProject: ActorAndProject, applicationName: String, groupId: Int?) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appName", applicationName)
                    setParameter("groupId", groupId)
                },
                """
                    update app_store.applications
                    set group_id = :groupId
                    where name = :appName
                """
            )
        }
    }

    suspend fun listGroups(actorAndProject: ActorAndProject): List<ApplicationGroup> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                """
                    select id, title, description, default_name, default_version
                    from application_groups
                    order by title
                """
            ).rows.mapNotNull { row ->
                ApplicationGroup(
                    row.getInt("id")!!,
                    row.getString("title")!!,
                    row.getString("description"),
                    row.defaultApplication()
                )
            }
        }
    }

    suspend fun retrieveGroup(actorAndProject: ActorAndProject, id: Int? = null, applicationName: String? = null): RetrieveGroupResponse {
        return db.withSession { session ->
            val group = session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("name", applicationName)
                },
                """
                    select
                        g.id,
                        g.title,
                        g.description,
                        g.default_name,
                        g.default_version,
                        jsonb_agg(t.tag) as tags
                    from
                        app_store.application_groups g
                        left join app_store.group_tags gt on g.id = gt.group_id
                        left join app_store.tags t on t.id = gt.tag_id
                    where
                        (:id::int is not null and g.id = :id)
                        or (
                            :name::text is not null
                            and g.id in (
                                select a.group_id
                                from app_store.applications a
                                where a.name = :name::text
                            )
                        )
                    group by
                        g.id, g.title, g.description, g.default_name, g.default_version                    
                """
            ).rows.firstOrNull()?.let { row ->
                ApplicationGroup(
                    row.getInt("id")!!,
                    row.getString("title")!!,
                    row.getString("description"),
                    row.defaultApplication(),
                    defaultMapper.decodeFromString<List<String?>>(row.getString("tags") ?: "[]").filterNotNull()
                )
            } ?: throw RPCException("Group not found", HttpStatusCode.NotFound)

            val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
                emptyList()
            } else {
                retrieveUserProjectGroups(actorAndProject, authenticatedClient)
            }

            val apps = session.sendPreparedStatement(
                {
                    setParameter("id", group.id)
                    setParameter("user", actorAndProject.actor.safeUsername())
                    setParameter("project", actorAndProject.project)
                    setParameter("project_groups", projectGroups)
                    setParameter("is_admin", Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role))
                },
                """
                    with
                        candidates as (
                            select a.name, max(a.created_at) most_recent
                            from app_store.applications a
                            where a.group_id = :id and (
                                :is_admin or
                                a.is_public or (
                                    cast(:project as text) is null and :user in (
                                        select p.username from app_store.permissions p where p.application_name = a.name
                                    )
                                ) or (
                                    cast(:project as text) is not null and exists (
                                        select p.project_group from app_store.permissions p where
                                            p.application_name = a.name and
                                            p.project = cast(:project as text) and
                                            p.project_group in (select unnest(:project_groups::text[]))
                                     )
                                )
                            )
                            group by a.name
                        ),
                        most_recent as (
                            select a.*
                            from
                                candidates c
                                join app_store.applications a on a.name = c.name and a.created_at = c.most_recent
                        )
                    select * from most_recent;
                """
            ).rows.map {
                it.toApplicationSummary()
            }

            RetrieveGroupResponse(group, apps)
        }
    }

    suspend fun create(actorAndProject: ActorAndProject, application: Application, content: String) {
        val username = actorAndProject.actor.safeUsername()
        val isProvider = username.startsWith(AuthProviders.PROVIDER_PREFIX)
        val providerName = username.removePrefix(AuthProviders.PROVIDER_PREFIX)

        val existing = db.withSession { session ->
            internalByNameAndVersion(
                session,
                application.metadata.name,
                application.metadata.version
            )
        }
        if (existing != null) throw ApplicationException.AlreadyExists()

        val existingTool = db.withSession { session ->
            toolDao.internalByNameAndVersion(
                session,
                application.invocation.tool.name,
                application.invocation.tool.version
            )
        } ?: throw ApplicationException.BadToolReference()

        if (isProvider) {
            val tool = existingTool.toTool()
            if (tool.description.backend != ToolBackend.NATIVE) throw ApplicationException.NotAllowed()
            if (tool.description.supportedProviders != listOf(providerName)) throw ApplicationException.NotAllowed()
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("owner", username)
                    setParameter("created_at", LocalDateTime.now())
                    setParameter("modified_at", LocalDateTime.now())
                    setParameter("authors", defaultMapper.encodeToString(application.metadata.authors))
                    setParameter("title", application.metadata.title)
                    setParameter("description", application.metadata.description)
                    setParameter("website", application.metadata.website)
                    setParameter("tool_name", existingTool.getString("name"))
                    setParameter("tool_version", existingTool.getString("version"))
                    setParameter("is_public", application.metadata.public)
                    setParameter("id_name", application.metadata.name)
                    setParameter("id_version", application.metadata.version)
                    setParameter("application", defaultMapper.encodeToString(application.invocation))
                    setParameter("original_document", content)
                },
                """
                    insert into app_store.applications
                        (name, version, application, created_at, modified_at, original_document, owner, tool_name, tool_version, authors, title, description, website) 
                    values 
                        (:id_name, :id_version, :application, :created_at, :modified_at, :original_document, :owner, :tool_name, :tool_version, :authors, :title, :description, :website)
                """
            )

            if (isProvider) {
                val tagId = session.sendPreparedStatement(
                    {
                        setParameter("provider_tag", providerName)
                    },
                    """
                    insert into tags (tag) values (:providerTag) returning id
                """
                ).rows.first().getInt(0)

                session.sendPreparedStatement(
                    {
                        setParameter("name", application.metadata.name)
                        setParameter("tag_id", tagId)
                    },
                    """
                    insert into application_tags (application_name, tag_id)
                    values (:name, :tag_id)
                """
                )
            }
        }

        elasticDao?.createApplicationInElastic(
            application.metadata.name,
            application.metadata.version,
            application.metadata.description,
            application.metadata.title
        )
    }


    suspend fun updatePage(page: AppStorePageType, sections: List<PageSection>) {
        db.withSession { session ->
            // Validation step
            val featuredGroups = session.sendPreparedStatement(
                {
                    setParameter("titles", sections.flatMap { it.featured.map { it.lowercase() } })
                },
                """
                    select id, title, description from application_groups where lower(title) in (select unnest(:titles::text[]))
                """
            ).rows.map {
                ApplicationGroup(
                    it.getInt("id")!!,
                    it.getString("title")!!
                )
            }

            // Check if the groups in the yaml file exists
            val sectionsNotFound = sections.flatMap {
                it.featured.map { it.lowercase() }
            } - featuredGroups.map { it.title.lowercase() }.toSet()

            if (sectionsNotFound.isNotEmpty()) {
                throw RPCException("Featured application group not found: ${sectionsNotFound.first()}", HttpStatusCode.NotFound)
            }

            // Check if the tags defined in the yaml file exists
            val allTags = session.sendPreparedStatement(
                """
                    select tag from app_store.tags     
                """
            ).rows.map { it.getString("tag") }

            val tagsNotFound = sections.flatMap { it.tags }.filter { !allTags.contains(it) }

            if (tagsNotFound.isNotEmpty()) {
                throw RPCException("Tag not found: ${tagsNotFound.first()}", HttpStatusCode.NotFound)
            }

            if (page == AppStorePageType.FULL) {
                sections.forEach { section ->
                    if (section.tags.isEmpty()) {
                        throw RPCException("Tag list cannot be empty for section ${section.title}", HttpStatusCode.BadRequest)
                    }
                }
            }

            // Update step
            session.sendPreparedStatement(
                {
                    setParameter("page", page.name)
                },
                """
                    delete from app_store.section_featured_items
                    where section_id in (
                        select id from app_store.sections where page = :page
                    )
                """
            )

            if (page == AppStorePageType.FULL) {
                session.sendPreparedStatement(
                    {
                        setParameter("page", page.name)
                    },
                    """
                        truncate app_store.section_tags
                    """
                )
            }

            session.sendPreparedStatement(
                {
                    setParameter("page", page.name)
                },
                """
                    delete from app_store.sections
                    where page = :page
                """
            )

            var sectionIndex = 0
            sections.forEach { section ->
                val sectionId = session.sendPreparedStatement(
                    {
                        setParameter("title", section.title)
                        setParameter("order", sectionIndex)
                        setParameter("page", page.name)
                    },
                    """
                        insert into app_store.sections
                        (title, order_index, page) values (
                            :title, :order, :page
                        )
                        returning id
                    """
                ).rows.first().getInt("id")

                val featuredGroupIds = section.featured.map { featuredSearchString ->
                    session.sendPreparedStatement(
                        {
                            setParameter("title", featuredSearchString.lowercase())
                        },
                        """
                            select id, title, description
                            from application_groups
                            where lower(title) = :title
                        """
                    ).rows.first().getInt("id")!!
                }

                var groupIndex = 0
                featuredGroupIds.forEach { featuredGroupId ->
                    session.sendPreparedStatement(
                        {
                            setParameter("section", sectionId)
                            setParameter("group_id", featuredGroupId)
                            setParameter("group_index", groupIndex)
                        },
                        """
                            insert into app_store.section_featured_items
                            (section_id, group_id, order_index) values (
                                :section, :group_id, :group_index
                            )
                        """
                    )
                    groupIndex += 1
                }

                if (page == AppStorePageType.FULL) {
                    session.sendPreparedStatement(
                        {
                            setParameter("section_id", sectionId)
                            setParameter("tags", section.tags)
                        },
                        """
                            with tmp as (
                                select :section_id as section,
                                    (select id
                                        from app_store.tags
                                        where tag in (select unnest(:tags::text[]))
                                    ) as tag
                            )
                            insert into app_store.section_tags (section_id, tag_id)
                            select section, tag from tmp
                        """
                    )
                }
                sectionIndex += 1
            }
        }
    }

    suspend fun delete(actorAndProject: ActorAndProject, appName: String, appVersion: String) {
        db.withSession { session ->
            // Prevent deletion of last version of application
            val isLast =
                findByName(actorAndProject, appName, NormalizedPaginationRequest(25, 0)).itemsInTotal <= 1

            if (isLast) {
                throw ApplicationException.NotAllowed()
            }

            val existingApp =
                internalByNameAndVersion(session, appName, appVersion) ?: throw ApplicationException.NotFound()

            cleanupBeforeDelete(
                session,
                existingApp.getString("name")!!,
                existingApp.getString("version")!!
            )

            session.sendPreparedStatement(
                {
                    setParameter("appname", existingApp.getString("name")!!)
                    setParameter("appversion", existingApp.getString("version"))
                },
                """
                    DELETE FROM applications
                    WHERE (name = :appname) AND (version = :appversion)
                """
            )
        }

        appEventProducer?.produce(
            AppEvent.Deleted(
                appName,
                appVersion
            )
        )

        elasticDao?.deleteApplicationInElastic(appName, appVersion)
    }

    suspend fun findAllByID(
        actorAndProject: ActorAndProject,
        projectGroups: List<String>,
        embeddedNameAndVersionList: List<EmbeddedNameAndVersion>,
        paging: NormalizedPaginationRequest
    ): List<Application> {
        if (embeddedNameAndVersionList.isEmpty()) {
            return emptyList()
        }
        val names = embeddedNameAndVersionList.map { it.name }
        val versions = embeddedNameAndVersionList.map { it.version }

        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("names", names)
                        setParameter("versions", versions)
                    },
                    """
                        SELECT *
                        FROM applications
                        WHERE (name, version) IN (select unnest(:names::text[]), unnest(:versions::text[]))
                    """
                )
                .rows
                .map { it.toApplicationWithInvocation() }
        }
    }

    internal suspend fun findAppNamesFromTags(
        actorAndProject: ActorAndProject,
        memberGroups: List<String>,
        tags: List<String>,
        excludeTools: List<String>?
    ): List<String> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("user", actorAndProject.actor.username)
                        setParameter("project", actorAndProject.project)
                        setParameter("groups", memberGroups)
                        setParameter("tags", tags)
                        setParameter("isAdmin", Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role))
                        setParameter("exclude", excludeTools?.map { it.toLowerCase() } ?: emptyList())
                    },
                    """
                        select distinct at.application_name as app_name
                        from application_tags as at, applications as a
                        where at.application_name = a.name and at.tag_id in (
                            select id from tags where lower(tag) in (select lower(unnest(:tags::text[])))
                        ) and (
                            (
                                a.is_public = true
                            ) or (
                                cast(:project as text) is null and :user in (
                                    select p1.username from permissions as p1 where p1.application_name = a.name
                                )
                            ) or (
                                cast(:project as text) is not null and exists (
                                    select p2.project_group from permissions as p2 where
                                        p2.application_name = a.name and
                                        p2.project = cast(:project as text) and
                                        p2.project_group in (select unnest(:groups::text[]))
                                )
                            ) or (
                                :isAdmin
                            )
                        ) and (a.tool_name not in (select unnest(:exclude::text[])))
                        order by at.application_name;
                    """
                )
                .rows
                .toList()
                .mapNotNull {
                    it.getString("app_name")
                }
        }
    }


    suspend fun findLatestByTool(
        actorAndProject: ActorAndProject,
        tool: String,
        paging: NormalizedPaginationRequest
    ): Page<Application> {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        val groups = if (projectGroups.isEmpty()) {
            listOf("")
        } else {
            projectGroups
        }

        val items = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("toolName", tool)
                    setParameter("user", actorAndProject.actor.username)
                    setParameter("isAdmin", Roles.PRIVILEGED.contains((actorAndProject.actor as? Actor.User)?.principal?.role))
                    setParameter("project", actorAndProject.project)
                    setParameter("groups", groups)
                },
                """
                SELECT A.*
                FROM applications AS A 
                WHERE 
                    (A.created_at) IN (
                        SELECT MAX(created_at)
                        FROM applications AS B
                        WHERE A.name = B.name
                        GROUP BY name
                    ) AND A.tool_name = :toolName AND (
                        (
                            A.is_public = TRUE
                        ) OR (
                            cast(:project as text) is null AND :user in (
                                SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                            )
                        ) OR (
                            cast(:project as text) is not null AND exists (
                                SELECT P2.project_group
                                FROM permissions AS P2
                                WHERE
                                    P2.application_name = A.name AND
                                    P2.project = cast(:project as text) AND
                                    P2.project_group in (:groups)
                            )
                        ) or (
                            :isAdmin
                        ) 
                    )
                order by A.name
            """
            )
                .rows
        }

        return Page(items.size, paging.itemsPerPage, paging.page, items.map { it.toApplicationWithInvocation() })
    }

    internal suspend fun findAppsFromAppNames(
        actorAndProject: ActorAndProject,
        memberGroups: List<String>,
        applicationNames: List<String>,
        excludeTools: List<String>?
    ): Pair<List<Application>, Int> {
        val items = db.withSession { session ->
            val excludeNormalized = excludeTools?.map {it.toLowerCase()} ?: emptyList()
            session
                .sendPreparedStatement(
                    {
                        setParameter("applications", applicationNames)
                        setParameter("user", actorAndProject.actor.username)
                        setParameter("project", actorAndProject.project)
                        setParameter("groups", memberGroups)
                        setParameter("isAdmin", Roles.PRIVILEGED.contains((actorAndProject.actor as Actor.User)?.principal?.role))
                        setParameter("exclude", excludeNormalized)
                    },
                    """
                        SELECT DISTINCT ON (A.name) A.*
                        FROM applications as A
                        WHERE (A.name) IN (select unnest(:applications::text[])) AND (
                            (
                                A.is_public = TRUE
                            ) or (
                                cast(:project as text) is null and :user in (
                                    SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                                )
                            ) or (
                                cast(:project as text) is not null AND exists (
                                    SELECT P2.project_group FROM permissions AS P2 WHERE
                                        P2.application_name = A.name and
                                        P2.project = cast(:project as text) and
                                        P2.project_group in (select unnest(:groups::text[]))
                                )
                            ) or (
                                :isAdmin
                            )
                        ) AND (A.tool_name NOT IN (select unnest(:exclude::text[])))
                        ORDER BY A.name, A.created_at DESC ;
                    """
                )
                .rows
                .toList()
                .map { it.toApplicationWithInvocation() }

        }
        return Pair(
            items, items.size
        )
    }

    suspend fun updateFlavor(actorAndProject: ActorAndProject, request: UpdateFlavorRequest) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appName", request.applicationName)
                    setParameter("flavor", request.flavorName)
                },
                """
                update app_store.applications
                set flavor_name = :flavor
                where name = :appName
            """
            )
        }
    }

    suspend fun preparePageForUser(
        ctx: DBContext,
        user: String?,
        page: Page<Application>
    ): Page<ApplicationWithFavoriteAndTags> {
        if (!user.isNullOrBlank()) {
            val allFavorites = ctx.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", user)
                        },
                        """
                            SELECT * 
                            FROM favorited_by
                            WHERE the_user = :user
                        """
                    )
                    .rows
            }

            val allApplicationsOnPage = page.items.map { it.metadata.name }.toSet()

            val allTagsForApplicationsOnPage = ctx.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("allapps", allApplicationsOnPage.toList())
                        },
                        """
                            select application_name, tag
                            from application_tags, tags
                            where application_name in (select unnest(:allapps::text[])) and tag_id = tags.id
                        """
                    )
                    .rows
            }
            val preparedPageItems = page.items.map { item ->
                val isFavorite = allFavorites.any { fav ->
                    fav.getString("application_name") == item.metadata.name &&
                        fav.getString("application_version") == item.metadata.version
                }

                val allTagsForApplication = allTagsForApplicationsOnPage
                    .filter { item.metadata.name == it.getString("application_name") }
                    .mapNotNull { it.getString("tag") }
                    .toSet()
                    .toList()

                ApplicationWithFavoriteAndTags(item.metadata, item.invocation, isFavorite, allTagsForApplication)
            }

            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        } else {
            val preparedPageItems = page.items.map { item ->
                val allTagsForApplication = ctx.withSession { session ->
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("appname", item.metadata.name)
                            },
                            """
                                select distinct tag
                                from application_tags, tags
                                where application_name = :appname and tag_id = tags.id
                            """
                        )
                        .rows
                        .mapNotNull {
                            it.getString("tag")
                        }
                }

                ApplicationWithFavoriteAndTags(item.metadata, item.invocation, false, allTagsForApplication)
            }
            return Page(page.itemsInTotal, page.itemsPerPage, page.pageNumber, preparedPageItems)
        }
    }

    private suspend fun cleanupBeforeDelete(ctx: DBContext, appName: String, appVersion: String) {
        ctx.withSession { session ->
            //DELETE FROM FAVORITES
            session
                .sendPreparedStatement(
                    {
                        setParameter("appname", appName)
                        setParameter("appversion", appVersion)
                    },
                    """
                        DELETE FROM favorited_by
                        WHERE (application_name = :appname) AND (application_version = :appversion)
                    """
                )
                .rows
        }
    }

    companion object : Loggable {
        override val log = logger()
    }

}

sealed class ApplicationException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ApplicationException("Not found", HttpStatusCode.NotFound)
    class NotAllowed : ApplicationException("Not allowed", HttpStatusCode.Forbidden)
    class AlreadyExists : ApplicationException("Already exists", HttpStatusCode.Conflict)
    class BadToolReference : ApplicationException("Tool does not exist", HttpStatusCode.BadRequest)
    class BadApplication : ApplicationException("Application does not exists", HttpStatusCode.NotFound)
}

internal fun RowData.toApplicationMetadata(): ApplicationMetadata {
    // TODO This shouldn't be crashing like this. Make sure we actually get all of the data needed.
    val group = try {
        ApplicationGroup(
            this.getInt("group_id")!!,
            this.getString("group_title")!!,
            this.getString("group_description"),
            this.defaultApplication()
        )
    } catch (e: Exception) {
        null
    }

    return ApplicationMetadata(
        this.getString("name")!!,
        this.getString("version")!!,
        defaultMapper.decodeFromString(this.getString("authors")!!),
        this.getString("title")!!,
        this.getString("description")!!,
        this.getString("website"),
        this.getBoolean("is_public")!!,
        this.getString("flavor_name"),
        group
    )
}

internal fun RowData.toApplicationSummary(): ApplicationSummary {
    return ApplicationSummary(toApplicationMetadata())
}

internal fun RowData.defaultApplication(): NameAndVersion? {
    // TODO This shouldn't be crashing like this. Make sure we actually get all of the data needed.
    return try {
        val defaultName = this.getString("default_name")
        val defaultVersion = this.getString("default_version")

        if (defaultName != null && defaultVersion != null) {
            NameAndVersion(defaultName, defaultVersion)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

internal fun RowData.toApplicationWithInvocation(): Application {
    val application = this.getString("application")!!
    val invocations = defaultMapper.decodeFromString<ApplicationInvocationDescription>(application)
    return Application(
        toApplicationMetadata(),
        invocations
    )
}
