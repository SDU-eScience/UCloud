package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.*
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
                    SELECT * FROM applications AS A
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

    suspend fun findGroup(
        actorAndProject: ActorAndProject,
        applicationName: String
    ): FindGroupResponse {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appName", applicationName)
                },
                """
                select a.name, a.version, a.authors, a.title, a.description, a.website, a.is_public, a.flavor_name, g.title as group
                from app_store.applications a
                join app_store.application_groups g on group_id = g.id
                where a.group_id = (
                    select group_id
                    from app_store.applications
                    where name = :appName
                )
            """
            ).rows.map { row ->
                val groupTitle = row.getString("group")!!
                val application = row.toApplicationSummary()

                groupTitle to application
            }.let { result ->
                val apps = result.map { it.second }

                FindGroupResponse(result.first().first, apps)
            }
        }
    }

    suspend fun browseSections(
        actorAndProject: ActorAndProject,
        pageType: AppStorePageType
    ): AppStoreSectionsResponse {
        /*
        val groups = if (memberGroups.isEmpty()) {
            listOf("")
        } else {
            memberGroups
        }

        val sections = ArrayList<AppStoreSection>()
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", user.username)
                    setParameter("is_admin", Roles.PRIVILEGED.contains(user.role))
                    setParameter("project", project)
                    setParameter("groups", groups)
                },
                """
                    select reference_id,
                        (select tag from tags where reference_type = 'TAG' and id = cast(reference_id as int) limit 1) tag_name,
                        a.name, a.version, a.authors, a.title, a.description, a.website, a.is_public,
                        reference_type,
                        exists(select * from favorited_by where the_user = :user and application_name = a.name) favorite,
                        (select json_agg(tag) from tags where id in (select tag_id from application_tags where application_name = a.name)) tags,
                        columns,
                        rows
                    from
                        overview,
                        (select * from applications a1 where created_at in (
                            select max(created_at)
                            from applications a2 where a1.name = a2.name and (
                                :is_admin or (
                                    a2.is_public or (
                                        cast(:project as text) is null and :user in (
                                            select p.username from app_store.permissions p where p.application_name = a1.name
                                        )
                                    ) or (
                                        cast(:project as text) is not null and exists (
                                            select p.project_group from app_store.permissions p where
                                                p.application_name = a1.name and
                                                p.project = cast(:project as text) and
                                                p.project_group in (select unnest(:groups::text[]))
                                         )
                                    )
                                )
                            )
                        ) order by name) a
                    join tools t on
                        a.tool_name = t.name and a.tool_version = t.version
                    where (reference_type = 'TOOL' and lower(tool_name) = lower(reference_id))
                        or (
                            reference_type = 'TAG' and
                            lower(a.name) in (select lower(application_name)
                                from application_tags
                                where tag_id in (select id from tags where id = cast(reference_id as int))
                            )
                        )
                    order by order_id, a.title
                """
            ).rows.forEach { row ->
                val refId = row.getString("reference_id")!!
                val tagName = row.getString("tag_name")
                val type = AppStoreSectionType.valueOf(row.getString("reference_type")!!)
                val applicationMetadata = ApplicationMetadata(
                    row.getString("name")!!,
                    row.getString("version")!!,
                    defaultMapper.decodeFromString(row.getString("authors") ?: "[]"),
                    row.getString("title")!!,
                    row.getString("description")!!,
                    row.getString("website"),
                    row.getBoolean("is_public")!!
                )
                val favorite = row.getBoolean("favorite")!!
                val columns = row.getInt("columns")!!
                val rows = row.getInt("rows")!!
                val tags = defaultMapper.decodeFromString<List<String>>(row.getString("tags") ?: "[]")
                val appWithFavorite = ApplicationSummaryWithFavorite(applicationMetadata, favorite, tags)

                val section = sections.find { it.name == tagName || it.name == refId }
                if (section != null) {
                    when (section) {
                        is AppStoreSection.Tag -> {
                            section.applications.add(appWithFavorite)
                        }
                        is AppStoreSection.Tool -> {
                            section.applications.add(appWithFavorite)
                        }
                    }
                } else {
                    val newSection = when (type) {
                        AppStoreSectionType.TAG -> {
                            AppStoreSection.Tag(
                                tagName ?: "",
                                arrayListOf(appWithFavorite),
                                columns,
                                rows
                            )
                        }
                        AppStoreSectionType.TOOL -> {
                            AppStoreSection.Tool(
                                refId,
                                arrayListOf(appWithFavorite),
                                columns,
                                rows
                            )
                        }
                    }

                    sections.add(newSection)
                }
            }
        }

        return sections
         */




        val groups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(actorAndProject, authenticatedClient)
        }

        return AppStoreSectionsResponse(
            db.withSession { session ->
                val sections = ArrayList<AppStoreSection>()

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
                                distinct on (g.id) g.id,
                                a.name,
                                a.version,
                                a.authors,
                                a.title,
                                a.description as app_description,
                                a.website,
                                a.is_public,
                                exists(select * from app_store.favorited_by where the_user = :user and application_name = a.name) favorite,
                                s.title as section,
                                g.title as title,
                                g.logo,
                                g.description,
                                f.order_index
                            from app_store.sections s
                                join app_store.section_featured_items f on f.section_id = s.id
                                join app_store.application_groups g on g.id = f.group_id
                                join app_store.applications a on g.id = a.group_id
                            where page = :page
                        )
                        select * from cte order by order_index
                    """
                ).rows.forEach { row ->
                    val sectionName = row.getString("section")!!
                    val groupId = row.getInt("id")!!
                    val groupTitle = row.getString("title")!!
                    val description = row.getString("description")
                    val applicationMetadata = ApplicationMetadata(
                        row.getString("name")!!,
                        row.getString("version")!!,
                        defaultMapper.decodeFromString(row.getString("authors") ?: "[]"),
                        row.getString("title")!!,
                        row.getString("app_description")!!,
                        row.getString("website"),
                        row.getBoolean("is_public")!!
                    )

                    val favorite = row.getBoolean("favorite")!!
                    val appWithFavorite = ApplicationSummaryWithFavorite(applicationMetadata, favorite, emptyList())

                    val section = sections.find { it.name == sectionName }

                    if (section == null) {
                        sections.add(
                            AppStoreSection(
                                sectionName,
                                mutableListOf(
                                    ApplicationGroup(
                                        groupId,
                                        groupTitle,
                                        null,
                                        description,
                                        appWithFavorite
                                    )
                                )
                            )
                        )
                    } else {
                        section.items.add(ApplicationGroup(groupId, groupTitle, null, description, appWithFavorite))
                    }
                }
                sections
            }
        )
    }

    // TODO(Brian)
    /*suspend fun findAll(
        actorAndProject: ActorAndProject,
        names: List<String>?,
        versions: List<String>?,
        fileExtensions
    ): PageV2<Application> {
        val result = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("names", names)
                    setParameter("versions", versions)
                },
                """
                    SELECT *
                    FROM applications
                    WHERE
                        (:names is null or name in (select unnest(:names:::text[]))) and
                        (:versions is null or version in (select unnest(:versions::text[])))
                """
            )
            .rows
            .map { it.toApplicationWithInvocation() }
        }

        return PageV2(50, result, null)
    }*/

    suspend fun createGroup(actorAndProject: ActorAndProject, title: String) {
        db.withSession { session ->
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

            session.sendPreparedStatement(
                {
                    setParameter("title", title)
                },
                """
                    insert into app_store.application_groups (title) values (:title)
                """
            )
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

    suspend fun updateGroup(actorAndProject: ActorAndProject, id: Int, title: String, logo: ByteArray? = null, description: String? = null) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("title", title)
                    setParameter("logo", logo)
                    setParameter("description", description)
                },
                """
                   update app_store.application_groups set title = :title, logo = :logo, description = :description where id = :id 
                """
            )
        }
    }

    suspend fun setGroup(actorAndProject: ActorAndProject, applicationName: String, groupId: Int) {
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
                    select * from application_groups
                """
            ).rows.mapNotNull { row ->
                ApplicationGroup(
                    row.getInt("id")!!,
                    row.getString("title")!!,
                    row.getAs<ByteArray>("logo"),
                    row.getString("description")
                )
            }
        }
    }

    suspend fun retrieveGroup(actorAndProject: ActorAndProject, id: Int): ApplicationGroup {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                },
                """
                    select * from app_store.application_groups where id = :id
                """
            ).rows.first().let {
                ApplicationGroup(
                    it.getInt("id")!!,
                    it.getString("title")!!,
                    it.getAs<ByteArray>("logo"),
                    it.getString("description")
                )
            }
        }
    }

    suspend fun saveLandingPage(content: String) {

    }

    suspend fun saveOverviewPage(content: String) {

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
                },
                """
                insert into app_store.applications
                    (name, version, application, created_at, modified_at, original_document, owner, tool_name, tool_version, authors, tags, title, description, website) 
                values 
                    (:id_name, :id_version, :application, :created_at, :modified_at, :original_document, :owner, :tool_name, :tool_version, :authors, :tags, :title, :description, :website)
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

    suspend fun delete(actorAndProject: ActorAndProject, appName: String, appVersion: String) {
        val projectGroups = if (actorAndProject.project.isNullOrBlank()) {
            emptyList()
        } else {
            retrieveUserProjectGroups(
                actorAndProject,
                authenticatedClient
            )
        }

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
    val group = try {
        ApplicationGroup(
            this.getInt("id")!!,
            this.getString("title")!!,
            this.getAs<ByteArray>("logo"),
            this.getString("description")
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

internal fun RowData.toApplicationWithInvocation(): Application {
    val application = this.getString("application")!!
    val invocations = defaultMapper.decodeFromString<ApplicationInvocationDescription>(application)
    return Application(
        toApplicationMetadata(),
        invocations
    )
}