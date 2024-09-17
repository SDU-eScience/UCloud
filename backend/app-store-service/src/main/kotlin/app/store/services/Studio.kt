package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.checkSingleLine
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.LookupByGroupTitleRequest
import dk.sdu.cloud.project.api.LookupProjectAndGroupRequest
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.DiscardingDBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.encodeToString
import org.imgscalr.Scalr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// Deals with access to the application studio

class Studio(
    // TODO(Dan): Eliminate the db variable from this file
    private val db: DBContext,

    private val data: CatalogData,
    private val serviceClient: AuthenticatedClient,
) {
    // Catalog services (write)
    // =================================================================================================================
    // This section of the code contain function to update the application catalog. Almost all calls in this section
    // are privileged. These calls will commonly update both the in-memory version of data and the database
    // immediately. When new applications are made, the appCreateMutex must be used.
    suspend fun createApplication(actorAndProject: ActorAndProject, application: Application) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)

        val curatorId = if (actorAndProject == ActorAndProject.System) {
            "main"
        } else {
            curators.values.firstOrNull { it.projectId == actorAndProject.project }?.id
                ?: throw RPCException("Not allowed to create application", HttpStatusCode.Forbidden)
        }

        val appWithCurator = application.copy(metadata = application.metadata.copy(curator = curatorId))
        data.registerApplication(appWithCurator, flush = true)
    }

    suspend fun createTool(actorAndProject: ActorAndProject, tool: Tool) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)

        val curatorId = if (actorAndProject == ActorAndProject.System) {
            "main"
        } else {
            curators.values.firstOrNull { it.projectId == actorAndProject.project }?.id
                ?: throw RPCException("Not allowed to create tool", HttpStatusCode.Forbidden)
        }

        val toolWithCurator = tool.copy(description = tool.description.copy(curator = curatorId))
        data.registerTool(toolWithCurator, flush = true)
    }

    suspend fun updateGroup(
        actorAndProject: ActorAndProject,
        id: Int,
        newTitle: String? = null,
        newDescription: String? = null,
        newDefaultFlavor: String? = null,
        newLogo: ByteArray? = null,
        newLogoHasText: Boolean? = null,
        newColorRemapping: ApplicationGroup.ColorReplacements? = null,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        val g = groups[id.toLong()] ?: throw RPCException("Unknown group: $id", HttpStatusCode.NotFound)
        val lookupByTitle = groups.filter { it.value.get().title == newTitle }

        if (lookupByTitle.isNotEmpty() && !lookupByTitle.keys.contains(id.toLong())) {
            throw RPCException("Group with title already exists", HttpStatusCode.Conflict)
        }

        val normalizedNewDescription = newDescription?.trim()
        if (newTitle != null) checkSingleLine("title", newTitle, maximumSize = 64)
        if (!normalizedNewDescription.isNullOrEmpty()) checkSingleLine("description", normalizedNewDescription, maximumSize = 600)

        val resizedLogo = if (newLogo != null) {
            if (newLogo.isEmpty()) {
                newLogo
            } else {
                val parsedLogo = ByteArrayInputStream(newLogo).use { ins ->
                    ImageIO.read(ins)
                } ?: throw RPCException("Invalid image file", HttpStatusCode.BadRequest)

                if (parsedLogo.width < DESIRED_LOGO_WIDTH) {
                    newLogo
                } else {
                    // Using QUALITY since a lot of the input images we get are already quite small
                    val resizedImage = Scalr.resize(
                        parsedLogo,
                        Scalr.Method.QUALITY,
                        Scalr.Mode.FIT_TO_WIDTH,
                        DESIRED_LOGO_WIDTH,
                        DESIRED_LOGO_WIDTH
                    )

                    ByteArrayOutputStream().use { outs ->
                        ImageIO.write(resizedImage, "PNG", outs)
                        outs.toByteArray()
                    }
                }
            }
        } else {
            null
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("newTitle", newTitle)
                    setParameter("newDescription", normalizedNewDescription)
                    setParameter("flavor", newDefaultFlavor)
                    setParameter("logo", resizedLogo)
                    setParameter("logo_has_text", newLogoHasText)
                    setParameter("color_remapping", newColorRemapping?.let { defaultMapper.encodeToString(it) })
                },
                """
                    update app_store.application_groups g
                    set
                        title = coalesce(:newTitle::text, g.title),
                        description = coalesce(:newDescription::text, g.description),
                        default_name = coalesce(:flavor::text, g.default_name),
                        logo = coalesce(:logo::bytea, g.logo),
                        logo_has_text = coalesce(:logo_has_text::bool, g.logo_has_text),
                        color_remapping = coalesce(:color_remapping::jsonb, g.color_remapping)
                    where g.id = :id
                """
            )

            if (newDefaultFlavor == "") {
                session.sendPreparedStatement(
                    { setParameter("id", id) },
                    """
                        update app_store.application_groups g
                        set default_name = null
                        where g.id = :id
                    """
                )
            }

            if (resizedLogo?.size == 0) {
                session.sendPreparedStatement(
                    { setParameter("id", id) },
                    """
                        update app_store.application_groups g
                        set logo = null
                        where g.id = :id
                    """
                )
            }
        }

        g.updateMetadata(newTitle, normalizedNewDescription, newDefaultFlavor, resizedLogo,
            newLogoHasText, newColorRemapping?.light, newColorRemapping?.dark)

        LogoGenerator.invalidateCache(g.get().title)
    }

    suspend fun assignApplicationToGroup(
        actorAndProject: ActorAndProject,
        appName: String,
        groupId: Int?,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        val newGroup =
            if (groupId == null) null
            else groups[groupId.toLong()] ?: throw RPCException("Unknown group", HttpStatusCode.NotFound)
        val allVersions = applicationVersions[appName]?.get()
            ?: throw RPCException("Unknown application: $appName", HttpStatusCode.NotFound)

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("group_id", groupId)
                    setParameter("name", appName)
                },
                """
                    update app_store.applications
                    set group_id = :group_id::int
                    where name = :name
                """
            )
        }

        for (version in allVersions) {
            val key = NameAndVersion(appName, version)
            val app = applications[key] ?: continue

            val currentGroupId = app.metadata.group
            var currentGroup: InternalGroup? = null
            if (currentGroupId != null) {
                currentGroup = groups[currentGroupId.metadata.id.toLong()]
                currentGroup?.removeApplications(setOf(key))
            }

            newGroup?.addApplications(setOf(key))

            applications[key] = app.copy(
                metadata = app.metadata.copy(
                    group = currentGroup?.toApiModel()
                )
            )
        }
    }

    suspend fun createGroup(
        actorAndProject: ActorAndProject,
        title: String,
    ): Int {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)

        val curatorId = if (actorAndProject == ActorAndProject.System) {
            "main"
        } else {
            curators.values.first { it.projectId == actorAndProject.project }?.id
                ?: throw RPCException("Curator not found", HttpStatusCode.NotFound)
        }

        val id = if (db == DiscardingDBContext) {
            groupIdAllocatorForTestsOnly.getAndIncrement()
        } else {
            db.withSession { session ->
                val existing = session.sendPreparedStatement(
                    { setParameter("title", title) },
                    """
                        select id from app_store.application_groups where title = :title
                    """
                ).rows.size

                if (existing > 0) {
                    throw RPCException("Group with name $title already exists", HttpStatusCode.Conflict)
                }

                try {
                    session.sendPreparedStatement(
                        {
                            setParameter("title", title)
                            setParameter("curator", curatorId)
                        },
                        """
                        insert into app_store.application_groups (title, curator)
                        values (:title, :curator)
                        returning id
                    """
                    ).rows.single().getInt(0)!!
                } catch (e: Exception) {
                    throw RPCException("Error creating group.", HttpStatusCode.BadRequest)
                }
            }
        }

        groups[id.toLong()] = InternalGroup(
            id.toLong(),
            title,
            "",
            null,
            null,
            emptySet(),
            emptySet(),
            false,
            null,
            null,
            curatorId
        )
        return id
    }

    suspend fun deleteGroup(
        actorAndProject: ActorAndProject,
        groupId: Int
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        groups[groupId.toLong()] ?: throw RPCException("Unknown group", HttpStatusCode.NotFound)
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("group_id", groupId) },
                """
                    delete from app_store.category_items
                    where group_id = :group_id
                """
            )

            session.sendPreparedStatement(
                { setParameter("group_id", groupId) },
                """
                    update app_store.applications
                    set group_id = null
                    where group_id = :group_id
                """
            )

            session.sendPreparedStatement(
                { setParameter("group_id", groupId) },
                """
                    delete from app_store.application_groups
                    where id = :group_id
                """
            )
        }

        val group = groups.remove(groupId.toLong())?.get() ?: return
        for (appRef in group.applications) {
            val app = applications[appRef] ?: continue
            applications[appRef] = app.copy(
                metadata = app.metadata.copy(
                    group = null
                )
            )
        }
    }

    suspend fun updatePublicFlag(
        actorAndProject: ActorAndProject,
        nameAndVersion: NameAndVersion,
        isPublic: Boolean,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        val app = applications.get(nameAndVersion) ?: throw RPCException("Unknown application", HttpStatusCode.NotFound)
        val newApp = app.copy(
            metadata = app.metadata.copy(
                public = isPublic
            )
        )

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("is_public", isPublic)
                    setParameter("name", nameAndVersion.name)
                    setParameter("version", nameAndVersion.version)
                },
                """
                    update app_store.applications a
                    set is_public = :is_public
                    where
                        name = :name
                        and version = :version
                """
            )
        }
        applications[nameAndVersion] = newApp
    }

    suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        name: String,
        changes: List<ACLEntryRequest>,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        if (changes.isEmpty()) return

        val acl = accessControlLists.computeIfAbsent(name) { InternalAcl(emptySet()) }
        val additions = HashSet<EntityWithPermission>()
        val revoked = HashSet<AccessEntity>()

        for (change in changes) {
            if (change.revoke) {
                revoked.add(change.entity)
            } else {
                val newEntity = if (!change.entity.project.isNullOrEmpty() && !change.entity.group.isNullOrEmpty()) {
                    val projectById = Projects.retrieve.call(
                        ProjectsRetrieveRequest(change.entity.project!!),
                        serviceClient
                    ).orNull()

                    val foundProject = projectById ?:
                    Projects.retrieve.call(
                        ProjectsRetrieveRequest(change.entity.project!!),
                        serviceClient
                    ).orRethrowAs {
                        throw RPCException("Project not found", HttpStatusCode.NotFound)
                    }

                    val groupByTitle = ProjectGroups.lookupByTitle.call(
                        LookupByGroupTitleRequest(
                            foundProject.id,
                            change.entity.group!!
                        ),
                        serviceClient
                    ).orNull()

                    val foundGroupId = groupByTitle?.groupId
                        ?: ProjectGroups.lookupProjectAndGroup.call(
                            LookupProjectAndGroupRequest(foundProject.id, change.entity.group!!), serviceClient
                        ).orRethrowAs {
                            throw RPCException("Group not found", HttpStatusCode.NotFound)
                        }.group.id

                    AccessEntity(null, foundProject.id, foundGroupId)
                } else if (!change.entity.user.isNullOrEmpty()) {
                    val foundUser = UserDescriptions.lookupUsers.call(
                        LookupUsersRequest(listOf(change.entity.user!!)),
                        serviceClient
                    ).orRethrowAs {
                        throw RPCException("User not found", HttpStatusCode.NotFound)
                    }.results

                    if (foundUser[change.entity.user] == null) {
                        throw RPCException("User not found", HttpStatusCode.NotFound)
                    }

                    change.entity
                } else {
                    throw RPCException("Invalid permission entry", HttpStatusCode.BadRequest)
                }


                additions.add(EntityWithPermission(newEntity, change.rights))
            }
        }

        acl.addEntries(additions)
        acl.removeEntries(revoked)

        db.withSession { session ->
            if (additions.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        additions.split {
                            into("usernames") { it.entity.user }
                            into("projects") { it.entity.project }
                            into("groups") { it.entity.group }
                            into("permissions") { it.permission.name }
                        }

                        setParameter("name", name)
                    },
                    """
                        with data as (
                            select
                                unnest(:usernames::text[]) as username,
                                unnest(:projects::text[]) as project,
                                unnest(:groups::text[]) as project_group,
                                unnest(:permissions::text[]) as permission
                        )
                        insert into app_store.permissions (application_name, username, permission, project, project_group) 
                        select :name, coalesce(username, ''), permission, coalesce(project, ''), coalesce(project_group, '')
                        from data
                        on conflict do nothing 
                    """
                )
            }

            if (revoked.isNotEmpty()) {
                session.sendPreparedStatement(
                    {
                        setParameter("name", name)
                        revoked.split {
                            into("usernames") { it.user }
                            into("projects") { it.project }
                            into("groups") { it.group }
                        }
                    },
                    """
                        with data as (
                            select
                                unnest(:usernames::text[]) username,
                                unnest(:projects::text[]) project,
                                unnest(:groups::text[]) project_group
                        )
                        delete from app_store.permissions p
                        using data d
                        where
                            p.application_name = :name
                            and (
                                p.username = d.username
                                or (
                                    p.project = d.project
                                    and p.project_group = d.project_group
                                )
                            )
                    """,
                    debug = true,
                )
            }
        }
    }

    suspend fun updateAppFlavorName(
        actorAndProject: ActorAndProject,
        appName: String,
        newFlavorName: String?,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val appVersions =
            applicationVersions[appName] ?: throw RPCException("Unknown application", HttpStatusCode.NotFound)

        val newFlavor = if (newFlavorName != "") { newFlavorName } else { null }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appName", appName)
                    setParameter("flavor", newFlavor)
                },
                """
                    update app_store.applications
                    set flavor_name = :flavor
                    where name = :appName
                """
            )
        }

        for (version in appVersions.get()) {
            val appRef = NameAndVersion(appName, version)
            val app = applications[appRef] ?: continue
            val newApp = app.copy(
                metadata = app.metadata.copy(
                    flavorName = newFlavorName
                )
            )
            applications[appRef] = newApp
        }
    }

    suspend fun addGroupToCategory(
        actorAndProject: ActorAndProject,
        categoryIds: List<Int>,
        groupId: Int,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val group = groups[groupId.toLong()] ?: throw RPCException("Unknown group: $groupId", HttpStatusCode.NotFound)
        for (categoryId in categoryIds) {
            if (db == DiscardingDBContext) {
                val category = categories[categoryId.toLong()]
                    ?: throw RPCException("Unknown category: $categoryId", HttpStatusCode.NotFound)

                category.addGroup(setOf(groupId))
                group.addCategories(setOf(categoryId))
            } else {
                db.withSession { session ->
                    val category = categories[categoryId.toLong()]
                        ?: throw RPCException("Unknown category: $categoryId", HttpStatusCode.NotFound)

                    session.sendPreparedStatement(
                        {
                            setParameter("category_id", categoryId)
                            setParameter("group_id", groupId)
                        },
                        """
                            insert into app_store.category_items (group_id, tag_id) 
                            values (:group_id, :category_id) on conflict (group_id, tag_id) do nothing 
                        """
                    )

                    category.addGroup(setOf(groupId))
                    group.addCategories(setOf(categoryId))
                }
            }
        }
    }

    suspend fun removeGroupFromCategories(
        actorAndProject: ActorAndProject,
        tagIds: List<Int>,
        groupId: Int,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val group = groups[groupId.toLong()] ?: throw RPCException("Unknown group: $groupId", HttpStatusCode.NotFound)
        for (tagId in tagIds) {
            db.withSession { session ->
                val entry = categories[tagId.toLong()]
                    ?: throw RPCException("Unknown tag: $tagId", HttpStatusCode.NotFound)

                session.sendPreparedStatement(
                    {
                        setParameter("tag_id", tagId)
                        setParameter("group_id", groupId)
                    },
                    """
                        delete from app_store.category_items
                        where
                            group_id = :group_id
                            and tag_id = :tag_id
                    """
                )

                entry.removeGroup(setOf(groupId))
                group.removeCategories(setOf(tagId))
            }
        }
    }

    fun retrieveAcl(actorAndProject: ActorAndProject, name: String): Collection<EntityWithPermission> {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        return accessControlLists[name]?.get() ?: emptySet()
    }

    suspend fun retrieveDetailedAcl(
        actorAndProject: ActorAndProject,
        name: String
    ): Collection<DetailedEntityWithPermission> {
        val list = retrieveAcl(actorAndProject, name)
        return list.mapNotNull { e ->
            val projectAndGroupLookup =
                if (!e.entity.project.isNullOrBlank() && !e.entity.group.isNullOrBlank()) {
                    ProjectGroups.lookupProjectAndGroup.call(
                        LookupProjectAndGroupRequest(e.entity.project!!, e.entity.group!!),
                        serviceClient,
                    ).orNull()
                } else {
                    null
                }

            if (projectAndGroupLookup == null && e.entity.user == null) {
                null
            } else {
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
                            e.entity.user,
                            null,
                            null
                        )
                    },
                    e.permission
                )
            }
        }
    }

    suspend fun updateTopPicks(
        actorAndProject: ActorAndProject,
        newPicks: List<TopPick>,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        for (slide in newPicks) {
            val app = slide.applicationName
            val group = slide.groupId
            if (app != null) {
                applicationVersions[app] ?: throw RPCException("Unknown linked application!", HttpStatusCode.NotFound)
            }

            if (group != null) {
                groups[group.toLong()] ?: throw RPCException("Unknown linked group!", HttpStatusCode.NotFound)
            }
        }

        topPicks.update { newPicks }

        db.withSession { session ->
            session.sendPreparedStatement(
                {},
                """
                    delete from app_store.top_picks where true
                """
            )

            session.sendPreparedStatement(
                {
                    newPicks.split {
                        into("app_names") { it.applicationName }
                        into("groups") { it.groupId }
                        into("descriptions") { it.description }
                    }
                    setParameter("priorities", List(newPicks.size) { it })
                },
                """
                    with data as (
                        select
                            unnest(:app_names::text[]) app_name,
                            unnest(:groups::int[]) group_id,
                            unnest(:descriptions::text[]) description,
                            unnest(:priorities::int[]) priority
                    )
                    insert into app_store.top_picks (application_name, group_id, description, priority) 
                    select app_name, group_id, description, priority
                    from data
                """
            )
        }
    }

    suspend fun createOrUpdateSpotlight(
        actorAndProject: ActorAndProject,
        id: Int?,
        title: String,
        description: String,
        active: Boolean,
        applications: List<TopPick>,
    ): Int {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val spotlightId = db.withSession { session ->
            val allocatedId = if (db == DiscardingDBContext) {
                id ?: groupIdAllocatorForTestsOnly.getAndIncrement()
            } else {
                session.sendPreparedStatement(
                    {
                        setParameter("title", title)
                        setParameter("id", id)
                        setParameter("description", description)
                        setParameter("active", active)
                    },
                    """
                        insert into app_store.spotlights (id, title, description, active)
                        values (coalesce(:id::int, nextval('app_store.spotlights_id_seq')), :title, :description, :active)
                        on conflict (id) do update set
                            title = excluded.title,
                            description = excluded.description,
                            active = excluded.active
                        returning id
                    """
                ).rows.single().getInt(0)!!
            }

            session.sendPreparedStatement(
                { setParameter("id", allocatedId) },
                """
                    delete from app_store.spotlight_items
                    where spotlight_id = :id
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("id", allocatedId)
                    setParameter("priorities", IntArray(applications.size) { it }.toList())
                    applications.split {
                        into("titles") { it.title }
                        into("application_names") { it.applicationName }
                        into("group_ids") { it.groupId }
                        into("descriptions") { it.description }
                    }
                },
                """
                    with data as (
                        select
                            unnest(:priorities::int[]) priority,
                            unnest(:titles::text[]) title,
                            unnest(:group_ids::int[]) group_id,
                            unnest(:application_names::text[]) application_name,
                            unnest(:descriptions::text[]) description
                    )
                    insert into app_store.spotlight_items (spotlight_id, application_name, group_id, description, priority) 
                    select :id, application_name, group_id, description, priority
                    from data
                """
            )

            val newSpotlight = Spotlight(title, description, applications, false, allocatedId)
            val spotlight = spotlights.computeIfAbsent(allocatedId.toLong()) {
                InternalSpotlight(newSpotlight)
            }

            spotlight.update { newSpotlight }

            allocatedId
        }

        if (active) {
            activateSpotlight(actorAndProject, spotlightId)
        }

        return spotlightId
    }

    suspend fun activateSpotlight(
        actorAndProject: ActorAndProject,
        id: Int,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        spotlights.values.forEach { spot -> spot.update { it.copy(active = false) } }
        val spotlight = spotlights[id.toLong()] ?: throw RPCException("Unknown spotlight", HttpStatusCode.NotFound)
        spotlight.update { it.copy(active = true) }

        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("id", id) },
                """
                    update app_store.spotlights
                    set active = (id = :id)
                    where true
                """
            )
        }
    }

    suspend fun deleteSpotlight(
        actorAndProject: ActorAndProject,
        id: Int,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val didRemove = spotlights.remove(id.toLong()) != null
        if (!didRemove) throw RPCException("Unknown spotlight", HttpStatusCode.NotFound)

        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("id", id) },
                """
                    delete from app_store.spotlight_items
                    where spotlight_id = :id
                """
            )

            session.sendPreparedStatement(
                { setParameter("id", id) },
                """
                    delete from app_store.spotlights
                    where id = :id
                """
            )
        }
    }

    suspend fun assignPriorityToCategory(
        actorAndProject: ActorAndProject,
        categoryId: Int,
        priority: Int,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val category =
            categories[categoryId.toLong()] ?: throw RPCException("Unknown category", HttpStatusCode.NotFound)
        val currentPriority = category.priority()
        if (currentPriority == priority) return

        for (cat in categories.values) {
            if (cat.priority() == priority) {
                cat.updatePriority(currentPriority)
            }
        }

        category.updatePriority(priority)

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("current_priority", currentPriority)
                    setParameter("priority", priority)
                    setParameter("id", categoryId)
                },
                """
                    update app_store.categories
                    set priority = :current_priority
                    where priority = :priority
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("priority", priority)
                    setParameter("id", categoryId)
                },
                """
                    update app_store.categories
                    set priority = :priority
                    where id = :id
                """
            )
        }
    }

    suspend fun createCategory(
        actorAndProject: ActorAndProject,
        specification: ApplicationCategory.Specification,
    ): Int {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val priority = categories.size

        val curatorId = if (actorAndProject == ActorAndProject.System) {
            "main"
        } else {
            curators.values.first { it.projectId == actorAndProject.project }?.id
                ?: throw RPCException("Not allowed to create category", HttpStatusCode.Forbidden)
        }

        val id = if (db == DiscardingDBContext) {
            groupIdAllocatorForTestsOnly.getAndIncrement()
        } else {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("title", specification.title)
                        setParameter("priority", priority)
                        setParameter("curator", curatorId)
                    },
                    """
                        insert into app_store.categories (tag, priority, curator) 
                        values (:title, :priority, :curator)
                        returning id
                    """
                ).rows.single().getInt(0)!!
            }
        }

        val category = InternalCategory(specification.title, emptySet(), priority, curatorId)
        categories[id.toLong()] = category
        return id
    }

    suspend fun deleteCategory(
        actorAndProject: ActorAndProject,
        categoryId: Int,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val category =
            categories[categoryId.toLong()] ?: throw RPCException("Unknown category", HttpStatusCode.NotFound)

        for (groupId in category.groups()) {
            runCatching { removeGroupFromCategories(actorAndProject, listOf(categoryId), groupId) }
        }

        categories.remove(categoryId.toLong())

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("category_id", categoryId)
                },
                """
                    delete from app_store.category_items
                    where tag_id = :category_id
                """
            )

            session.sendPreparedStatement(
                {
                    setParameter("category_id", categoryId)
                },
                """
                    delete from app_store.categories
                    where id = :category_id
                """
            )
        }

        reorderCategories()
    }

    private suspend fun reorderCategories() {
        val allCategories = categories.toList().sortedBy { it.second.priority() }
        for ((index, idAndCategory) in allCategories.withIndex()) {
            val category = idAndCategory.second
            category.updatePriority(index)
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    allCategories.split {
                        into("priorities") { it.second.priority() }
                        into("ids") { it.first.toInt() }
                    }
                },
                """
                    with data as (
                        select unnest(:priorities::int[]) as priority, unnest(:ids::int[]) as id
                    )
                    update app_store.categories c
                    set priority = d.priority
                    from data d
                    where c.id = d.id
                """
            )
        }
    }

    suspend fun updateCarrousel(
        actorAndProject: ActorAndProject,
        newSlides: List<CarrouselItem>
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        for (slide in newSlides) {
            val app = slide.linkedApplication
            val group = slide.linkedGroup
            if (app != null) {
                applicationVersions[app] ?: throw RPCException("Unknown linked application!", HttpStatusCode.NotFound)
            }

            if (group != null) {
                groups[group.toLong()] ?: throw RPCException("Unknown linked group!", HttpStatusCode.NotFound)
            }
        }

        carrousel.update { newSlides }
        carrousel.updateImages { oldImages ->
            List(newSlides.size) { i -> oldImages.getOrNull(i) ?: ByteArray(0) }
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    newSlides.split {
                        into("titles") { it.title }
                        into("bodies") { it.body }
                        into("image_credits") { it.imageCredit }
                        into("linked_web_pages") { it.linkedWebPage }
                        into("linked_groups") { it.linkedGroup }
                        into("linked_applications") { it.linkedApplication }
                    }
                    setParameter("priorities", IntArray(newSlides.size) { it }.toList())
                },
                """
                    with data as (
                        select 
                            unnest(:titles::text[]) title,
                            unnest(:bodies::text[]) body, 
                            unnest(:image_credits::text[]) image_credit,
                            unnest(:linked_applications::text[]) linked_app,
                            unnest(:linked_web_pages::text[]) linked_web,
                            unnest(:priorities::int[]) priority,
                            unnest(:linked_groups::int[]) linked_group
                    )
                    insert into app_store.carrousel_items
                        (title, body, image_credit, linked_application, linked_web_page, priority, linked_group, image) 
                    select title, body, image_credit, linked_app, linked_web, priority, linked_group, ''::bytea
                    from data
                    on conflict (priority) do update set
                        title = excluded.title,
                        body = excluded.body,
                        image_credit = excluded.image_credit,
                        linked_application = excluded.linked_application,
                        linked_web_page = excluded.linked_web_page,
                        linked_group = excluded.linked_group
                """
            )
        }
    }

    suspend fun updateCarrouselImage(
        actorAndProject: ActorAndProject,
        slideIndex: Int,
        image: ByteArray
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        carrousel.updateImages {
            val copy = ArrayList(it)
            if (slideIndex in it.indices) {
                copy[slideIndex] = image
            }

            copy
        }

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("index", slideIndex)
                    setParameter("image", image)
                },
                """
                    update app_store.carrousel_items
                    set image = :image
                    where priority = :index
                """
            )
        }
    }

    fun retrieveSpotlights(
        actorAndProject: ActorAndProject,
        id: Int,
    ): Spotlight? {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        return spotlights[id.toLong()]?.get()
    }

    fun listSpotlights(
        actorAndProject: ActorAndProject,
    ): List<Spotlight> {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        return spotlights.values.map { it.get() }
    }

    suspend fun listGroups(actorAndProject: ActorAndProject): List<ApplicationGroup> {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val results = if (!actorAndProject.project.isNullOrEmpty()) {
            val curatorId = curators.values.first { it.projectId == actorAndProject.project }?.id
            if (curatorId == null) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
            groups.filter { curatorId == it.value.get().curator }
        } else if (actorAndProject == ActorAndProject.System) {
            groups
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        return results.mapNotNull { (groupId, _) ->
            groups[groupId]?.toApiModel()
        }.sortedBy { it.specification.title }
    }

    suspend fun retrieveGroup(
        actorAndProject: ActorAndProject,
        groupId: Int,
    ): ApplicationGroup? {
        val group = groups[groupId.toLong()]?.toApiModel() ?: return null
        return group.copy(
            status = group.status.copy(
                applications = listApplicationsInGroup(actorAndProject, groupId).map { it.withoutInvocation() }
            )
        )
    }

    suspend fun listApplicationsInGroup(
        actorAndProject: ActorAndProject,
        groupId: Int
    ): List<ApplicationWithFavoriteAndTags> {
        val group = groups[groupId.toLong()]?.get() ?: return emptyList()
        return loadApplications(actorAndProject, group.applications).sortedBy {
            it.metadata.flavorName?.lowercase() ?: it.metadata.name.lowercase()
        }
    }

    private suspend fun loadApplications(
        actorAndProject: ActorAndProject,
        versions: Collection<NameAndVersion>,
        withAllVersions: Boolean = false,
    ): List<ApplicationWithFavoriteAndTags> {
        val result = ArrayList<Application>()
        val isPrivileged = isPrivileged(actorAndProject)
        if (!isPrivileged) return emptyList()

        for (nameAndVersion in versions) {
            val app = applications[nameAndVersion] ?: continue

            val toolKey = app.invocation.tool.let { NameAndVersion(it.name, it.version) }
            val tool = tools[toolKey] ?: continue
            val group = app.metadata.group?.metadata?.id?.let { id ->
                retrieveGroup(actorAndProject, id)
            }

            result.add(
                app.copy(
                    metadata = app.metadata.copy(
                        group = group,
                    ),
                    invocation = app.invocation.copy(
                        tool = app.invocation.tool.copy(tool = tool)
                    )
                )
            )
        }

        val deduped = if (!withAllVersions) {
            result.groupBy { it.metadata.name }.map { (_, versions) ->
                versions.maxBy { it.metadata.createdAt }
            }
        } else {
            result
        }

        return deduped.map {
            ApplicationWithFavoriteAndTags(
                it.metadata,
                it.invocation,
                false,
                emptyList(),
            )
        }
    }

    fun listAllApplications(): List<NameAndVersion> {
        return applicationVersions.flatMap { (name, versions) -> versions.get().map { NameAndVersion(name, it) } }
    }

    fun listAllTools(): List<NameAndVersion> {
        return toolVersions.flatMap { (name, versions) -> versions.get().map { NameAndVersion(name, it) } }
    }

    fun listCategories(): List<ApplicationCategory> {
        return categories.toList()
            .sortedBy { it.second.priority() }
            .map { (k, v) ->
                ApplicationCategory(
                    ApplicationCategory.Metadata(
                        k.toInt(),
                    ),
                    ApplicationCategory.Specification(
                        v.title(),
                        "",
                        v.curator()
                    )
                )
            }
    }
}