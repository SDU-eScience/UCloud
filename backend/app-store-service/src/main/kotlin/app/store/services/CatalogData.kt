package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.util.AsyncCache
import dk.sdu.cloud.accounting.util.CyclicArray
import dk.sdu.cloud.accounting.util.IProjectCache
import dk.sdu.cloud.accounting.util.ThreadSafeCyclicArray
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.toTimestamp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import org.cliffc.high_scale_lib.NonBlockingHashMap
import org.cliffc.high_scale_lib.NonBlockingHashMapLong

// Deals only with loading and storing applications and their associated abstractions

// Storage
// =================================================================================================================
// The application service is optimized for workloads that have a lot of random reads (of a globally accessible
// data) with very rare writes. Given this access pattern, the service heavily prefers reading immutable data in a
// way that does not require ever holding a mutex. The NonBlockingHashMap data-structure is commonly used to hold
// a lookup table. The values stored in the table themselves typically hold an atomic reference to immutable data
// which is swapped out when updates are made. This approach allows us to never require a read or write lock when
// reading and updating the data. This comes at the cost of writes being slightly more expensive, but this is not
// a big deal in this scenario since writes are very rare.
//
// The data for these tables are loaded at startup via the reloadData() function.

// Stores a lookup table of all tools and a mapping of all versions of a single tool
val tools = NonBlockingHashMap<NameAndVersion, Tool>()
val toolVersions = NonBlockingHashMap<String, InternalVersions>()

// Stores a lookup table of all apps and a mapping of all versions of a single app
val applications = NonBlockingHashMap<NameAndVersion, Application>()
val applicationVersions = NonBlockingHashMap<String, InternalVersions>()

// Lookup tables for the hierarchical structuring of applications in the catalog
val groups = NonBlockingHashMapLong<InternalGroup>()
val categories = NonBlockingHashMapLong<InternalCategory>()
val curators = NonBlockingHashMap<String, InternalCurator>()

// Access control lists allow ordinary users to access non-public applications
val accessControlLists = NonBlockingHashMap<String, InternalAcl>()

// The landing page has various UI components which advertise specific applications. These lookup tables control
// those widgets.
val spotlights = NonBlockingHashMapLong<InternalSpotlight>()
val topPicks = InternalTopPicks(emptyList())
val carrousel = InternalCarrousel(emptyList())

// We track new applications and new updates (for the landing page). These are just ordinary cyclic arrays with a
// small capacity. These are not thread-safe, so we use a mutex for the rare writes.
val newApplications = ThreadSafeCyclicArray<String>(5)
val newUpdates = ThreadSafeCyclicArray<NameAndVersion>(5)

// Users can mark an application with a star, which allows for easy access. This is done at the application level
// but with no specific version. As a result, when you ask for your starred applications, you will get the most
// recent version from looking up in applicationVersions[starred].
val stars = NonBlockingHashMap<String, InternalStars>()

class CatalogData(
    private val db: DBContext,
) {
    // TODO(Dan): This function is currently not able to actually reloadData. It is only capable of loading data.
    //  We should add functionality which allows us to reload parts of the database as needed.
    suspend fun reloadData(id: NameAndVersion? = null) {
        db.withSession { session ->
            val toolRows = session.sendPreparedStatement(
                {
                    setParameter("name", id?.name)
                    setParameter("version", id?.version)
                },
                """
                    select *
                    from
                        app_store.tools
                    where 
                        (
                            name = :name::text
                            or :name::text is null
                        )
                        and (
                            version = :version::text
                            or :version::text is null
                        )
                """
            ).rows

            toolRows.forEach { row ->
                val tool = Tool(
                    row.getString("owner")!!,
                    row.getDate("created_at")!!.toTimestamp(),
                    row.getDate("modified_at")!!.toTimestamp(),
                    defaultMapper.decodeFromString<NormalizedToolDescription>(row.getString("tool")!!),
                )

                registerTool(tool, flush = false)
            }

            val curatorRows = session.sendPreparedStatement(
                """
                    select id, public_read, can_create_categories, can_manage_catalog, managed_by_project_id from app_store.curators
                """
            )

            curatorRows.rows.forEach { row ->
                val id = row.getString("id")!!
                val publicRead = row.getBoolean("public_read")!!
                val canCreateCategories = row.getBoolean("can_create_categories")!!
                val canManageCatalog = row.getBoolean("can_manage_catalog")!!
                val projectId = row.getString("managed_by_project_id")!!

                curators.computeIfAbsent(id) {
                    InternalCurator(id, publicRead, canCreateCategories, canManageCatalog, projectId)
                }
            }

            val appRows = session.sendPreparedStatement(
                {
                    setParameter("name", id?.name)
                    setParameter("version", id?.version)
                },
                """
                    select
                        a.*,
                        ag.id as group_id,
                        ag.title as group_title,
                        ag.description as group_description,
                        ag.default_name as default_name,
                        ag.logo as group_logo,
                        ag.logo_has_text as group_logo_has_text,
                        ag.color_remapping as color_remapping,
                        ag.curator as curator_id
                    from
                        app_store.applications a
                        left join app_store.application_groups ag
                            on ag.id = a.group_id
                    where
                        (
                            name = :name::text
                            or :name::text is null
                        )
                        and (
                            version = :version::text
                            or :version::text is null
                        )
                    order by
                        created_at
                """
            ).rows

            val appGroups = session.sendPreparedStatement("""
                select 
                    id,
                    title,
                    description,
                    default_name,
                    logo_has_text,
                    logo,
                    color_remapping,
                    curator 
                from app_store.application_groups
            """).rows

            for (row in appGroups) {
                val id = row.getInt("id") ?: continue
                val title = row.getString("title") ?: continue
                val defaultFlavor = row.getString("default_name")
                val description = row.getString("description") ?: ""
                val logo = row.getAs<ByteArray?>("logo")
                val logoHasText = row.getBoolean("logo_has_text") ?: false

                val colorRemapping = row.getString("color_remapping")?.let {
                    defaultMapper.decodeFromString<Map<String, Map<Int, Int>?>>(it)
                }

                val groupCurator = row.getString("curator")!!
                val darkRemapping = colorRemapping?.get("dark")
                val lightRemapping = colorRemapping?.get("light")

                val group = ApplicationGroup(
                    ApplicationGroup.Metadata(id),
                    ApplicationGroup.Specification(
                        title,
                        description,
                        defaultFlavor,
                        colorReplacement = ApplicationGroup.ColorReplacements(lightRemapping, darkRemapping),
                        logoHasText = logoHasText,
                        curator = groupCurator
                    ),
                )

                registerGroup(group)

                groups[id.toLong()]?.updateMetadata(
                    logo = logo,
                    colorRemappingLight = lightRemapping,
                    colorRemappingDark = darkRemapping
                )
            }

            appRows.forEach { row ->
                val app = row.toApplication()
                registerApplication(app, flush = false)
            }

            val acls = session
                .sendPreparedStatement(
                    {
                        setParameter("name", id?.name)
                    },
                    """
                        select
                            p.application_name,
                            p.username,
                            p.project,
                            p.project_group,
                            p.permission
                        from app_store.permissions p
                        where
                            application_name = :name::text
                            or :name::text is null
                    """.trimIndent()
                )
                .rows
                .map {
                    val app = it.getString(0)!!
                    val username = it.getString(1).takeIf { !it.isNullOrEmpty() }
                    val projectId = it.getString(2).takeIf { !it.isNullOrEmpty() }
                    val group = it.getString(3).takeIf { !it.isNullOrEmpty() }
                    val permission = ApplicationAccessRight.valueOf(it.getString(4)!!)

                    val entity = EntityWithPermission(
                        AccessEntity(username, projectId, group),
                        permission,
                    )

                    app to entity
                }
                .groupBy { it.first }
                .mapValues { (_, v) -> v.map { it.second } }

            for ((app, acl) in acls) {
                accessControlLists[app] = InternalAcl(acl.toSet())
            }

            if (id == null) {
                val rawTagInfo = session.sendPreparedStatement(
                    {},
                    """
                        select t.id, t.tag, t.priority, t.curator
                        from app_store.categories t
                    """
                ).rows
                for (row in rawTagInfo) {
                    val tagId = row.getInt(0)!!
                    val tag = row.getString(1)!!
                    val priority = row.getInt(2)!!
                    val curator = row.getString(3)!!
                    categories.computeIfAbsent(tagId.toLong()) { InternalCategory(tag, emptySet(), priority, curator) }
                }

                val tagRows = session.sendPreparedStatement(
                    {},
                    """
                        select
                            gt.group_id,
                            gt.tag_id
                        from
                            app_store.category_items gt
                    """
                ).rows

                tagRows.forEach { row ->
                    val groupId = row.getInt(0)!!
                    val tag = row.getInt(1)!!
                    registerGroupTag(groupId, tag)
                }

                val starRows = session.sendPreparedStatement(
                    {},
                    """
                        select the_user, application_name
                        from app_store.favorited_by
                    """
                ).rows

                for (row in starRows) {
                    val username = row.getString(0)!!
                    val applicationName = row.getString(1)!!
                    setStar(
                        ActorAndProject(Actor.SystemOnBehalfOfUser(username), null),
                        true,
                        applicationName,
                        flush = false
                    )
                }

                session.sendPreparedStatement(
                    {},
                    """
                        select id, title, description, active 
                        from app_store.spotlights
                    """
                ).rows.forEach { row ->
                    val spotlightId = row.getInt(0)!!
                    val title = row.getString(1)!!
                    val description = row.getString(2)!!
                    val active = row.getBoolean(3)!!
                    spotlights[spotlightId.toLong()] = InternalSpotlight(Spotlight(title, description, emptyList(), active, spotlightId))
                }

                session.sendPreparedStatement(
                    {},
                    """
                        select spotlight_id, application_name, group_id, description
                        from app_store.spotlight_items
                        order by spotlight_id, priority
                    """
                ).rows.forEach { row ->
                    val spotlightId = row.getInt(0)!!
                    val appName = row.getString(1)
                    val groupId = row.getInt(2)
                    val description = row.getString(3)!!

                    spotlights[spotlightId.toLong()]?.update {
                        it.copy(applications = it.applications + TopPick("", appName, groupId, description))
                    }
                }

                val loadedPicks = ArrayList<TopPick>()
                session.sendPreparedStatement(
                    {},
                    """
                        select application_name, group_id, description
                        from app_store.top_picks
                        order by priority
                    """
                ).rows.forEach { row ->
                    val appName = row.getString(0)
                    val groupId = row.getInt(1)
                    val description = row.getString(2)!!

                    loadedPicks.add(TopPick("", appName, groupId, description))
                }
                topPicks.update { loadedPicks }

                val loadedCarrousel = ArrayList<CarrouselItem>()
                val images = ArrayList<ByteArray>()
                session.sendPreparedStatement(
                    {},
                    """
                        select title, body, image_credit, linked_application, linked_web_page, linked_group, image
                        from app_store.carrousel_items
                        order by priority
                    """
                ).rows.forEach { row ->
                    val title = row.getString(0)!!
                    val body = row.getString(1)!!
                    val imageCredit = row.getString(2)!!
                    val linkedApplication = row.getString(3)
                    val linkedWebPage = row.getString(4)
                    val linkedGroup = row.getInt(5)
                    val image = row.getAs(6) ?: ByteArray(0)

                    loadedCarrousel.add(
                        CarrouselItem(
                            title, body, imageCredit, linkedApplication,
                            linkedWebPage, linkedGroup
                        )
                    )
                    images.add(image)
                }
                carrousel.update { loadedCarrousel }
                carrousel.updateImages { images }

                // Reorder categories
                val allCategories = categories.toList().sortedBy { it.second.priority() }
                for ((index, idAndCategory) in allCategories.withIndex()) {
                    val category = idAndCategory.second
                    category.updatePriority(index)
                }

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
    }

    private fun RowData.toApplicationMetadata(): ApplicationMetadata {
        val group = if (this.getInt("group_id") != null && this.getString("group_title") != null) {
            ApplicationGroup(
                ApplicationGroup.Metadata(
                    this.getInt("group_id")!!,
                ),
                ApplicationGroup.Specification(
                    this.getString("group_title")!!,
                    this.getString("group_description") ?: "",
                    this.getString("default_name"),
                    emptySet(),
                )
            )
        } else {
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
            group,
            this.getString("curator")!!,
            this.getDate("created_at")!!.toTimestamp(),
        )
    }

    private fun RowData.toApplication(): Application {
        val application = this.getString("application")!!
        val invocations = defaultMapper.decodeFromString<ApplicationInvocationDescription>(application)
        return Application(
            toApplicationMetadata(),
            invocations
        )
    }

    suspend fun registerApplication(inputApp: Application, flush: Boolean) {
        var app = inputApp
        val key = NameAndVersion(app.metadata.name, app.metadata.version)
        if (applications[key] != null) {
            // NOTE(Dan): We purposefully don't deal with the race conflict we have here. I don't believe it is worth
            // complicating the rest of the code to deal with it. It seems extremely unlikely that anyone would need to
            // depend on this not potentially having a race condition.
            throw RPCException("This application already exists!", HttpStatusCode.Conflict)
        }

        val appVersions = applicationVersions.computeIfAbsent(key.name) { InternalVersions() }
        appVersions.add(key.version)
        val allVersions = appVersions.get()
        val previousVersion = if (allVersions.size >= 2) allVersions[allVersions.size - 2] else null
        val previousApp = previousVersion?.let { v -> applications[NameAndVersion(app.metadata.name, v)] }

        val tool = app.invocation.tool.let { t -> tools[NameAndVersion(t.name, t.version)] }
        if (tool == null) {
            throw RPCException("This tool does not exist: ${app.invocation.tool}", HttpStatusCode.BadRequest)
        }

        val group = app.metadata.group ?: previousApp?.metadata?.group
        if (group != null) {
            val groupCache = registerGroup(group)
            groupCache.addApplications(setOf(key))
        }

        app = app.copy(
            metadata = app.metadata.copy(
                flavorName = app.metadata.flavorName ?: previousApp?.metadata?.flavorName,
                group = group,
            )
        )
        applications[key] = app

        val curator = if (!app.metadata.curator.isNullOrEmpty()) {
            app.metadata.curator
        } else {
            "main"
        }

        if (flush) {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("owner", "_ucloud")
                        setParameter("created_at", app.metadata.createdAt)
                        setParameter("modified_at", app.metadata.createdAt)
                        setParameter("authors", defaultMapper.encodeToString(app.metadata.authors))
                        setParameter("title", app.metadata.title)
                        setParameter("description", app.metadata.description)
                        setParameter("website", app.metadata.website)
                        setParameter("tool_name", app.invocation.tool.name)
                        setParameter("tool_version", app.invocation.tool.version)
                        setParameter("is_public", app.metadata.public)
                        setParameter("id_name", app.metadata.name)
                        setParameter("id_version", app.metadata.version)
                        setParameter("application", defaultMapper.encodeToString(app.invocation))
                        setParameter("original_document", "{}")
                        setParameter("group", group?.metadata?.id)
                        setParameter("flavor", app.metadata.flavorName ?: previousApp?.metadata?.flavorName)
                        setParameter("curator", curator)
                    },
                    """
                        insert into app_store.applications
                            (name, version, application, created_at, modified_at, original_document, owner, tool_name, tool_version, authors, title, description, website, group_id, flavor_name, is_public, curator) 
                        values 
                            (:id_name, :id_version, :application, to_timestamp(:created_at / 1000.0), to_timestamp(:modified_at / 1000.0), :original_document, :owner, :tool_name, :tool_version, :authors, :title, :description, :website, :group, :flavor, :is_public, :curator)
                    """
                )
            }
        }

        if (appVersions.get().size == 1) newApplications.add(app.metadata.name)
        newUpdates.add(NameAndVersion(app.metadata.name, app.metadata.version))
    }

    suspend fun registerTool(tool: Tool, flush: Boolean) {
        val key = tool.description.info
        if (tools[key] != null) {
            // NOTE(Dan): We purposefully don't deal with the race conflict we have here. I don't believe it is worth
            // complicating the rest of the code to deal with it. It seems extremely unlikely that anyone would need to
            // depend on this not potentially having a race condition.
            throw RPCException("This tool already exists!", HttpStatusCode.Conflict)
        }
        tools[key] = tool
        toolVersions.computeIfAbsent(key.name) { InternalVersions() }.add(key.version)

        val curator = if (!tool.description.curator.isNullOrEmpty()) {
            tool.description.curator
        } else {
            "main"
        }

        if (flush) {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("owner", "_ucloud")
                        setParameter("created_at", tool.createdAt)
                        setParameter("modified_at", tool.createdAt)
                        setParameter("tool", defaultMapper.encodeToString(tool.description))
                        setParameter("original_document", "{}")
                        setParameter("name", key.name)
                        setParameter("version", key.version)
                        setParameter("curator", curator)
                    },
                    """
                        insert into app_store.tools
                            (name, version, created_at, modified_at, original_document, owner, tool, curator) 
                        values 
                            (:name, :version, to_timestamp(:created_at / 1000.0), to_timestamp(:modified_at / 1000.0), :original_document, :owner, :tool, :curator)
                    """
                )
            }
        }
    }

    private fun registerGroup(group: ApplicationGroup): InternalGroup {
        return groups.computeIfAbsent(group.metadata.id.toLong()) {
            InternalGroup(
                group.metadata.id.toLong(),
                group.specification.title,
                group.specification.description,
                group.specification.defaultFlavor,
                null,
                emptySet(),
                emptySet(),
                group.specification.logoHasText,
                group.specification.colorReplacement.light,
                group.specification.colorReplacement.dark,
                group.specification.curator
            )
        }
    }

    private fun registerGroupTag(group: Int, tag: Int) {
        groups[group.toLong()]?.addCategories(setOf(tag))
        categories[tag.toLong()]?.addGroup(setOf(group))
    }

    fun retrieveCarrouselImage(index: Int): ByteArray {
        return carrousel.getImages().getOrNull(index) ?: ByteArray(0)
    }

    fun retrieveRawGroupLogo(
        groupId: Int,
    ): ByteArray? {
        val g = groups[groupId.toLong()]?.get() ?: return null
        return g.logo
    }

    suspend fun retrieveGroupLogo(
        groupId: Int,
        darkMode: Boolean = false,
        includeText: Boolean = false,
        placeTextUnderLogo: Boolean = false,
        flavorName: String? = null,
    ): ByteArray? {
        val g = groups[groupId.toLong()]?.get() ?: return null
        val logo = g.logo ?: LogoGenerator.emptyImage
        val hasLogo = g.logo != null

        val fullTitle = buildString {
            append(g.title.trim())
            if (flavorName != null) {
                append(" (")
                append(flavorName)
                append(")")
            }
        }

        val replacements = if (darkMode) g.colorRemappingDark else g.colorRemappingLight
        val cacheKey = buildString {
            append(groupId)
            append(fullTitle)
            append(darkMode)
            append(includeText)
            append(placeTextUnderLogo)
        }

        return LogoGenerator.generateLogoWithText(
            cacheKey,
            if ((includeText && !g.logoHasText) || !hasLogo) fullTitle else "",
            logo,
            placeTextUnderLogo,
            if (darkMode) DarkBackground else LightBackground,
            replacements ?: emptyMap(),
        )
    }

    // Starred applications
    // =================================================================================================================
    suspend fun toggleStar(
        actorAndProject: ActorAndProject,
        application: String,
    ) {
        val isStarred = stars
            .computeIfAbsent(actorAndProject.actor.safeUsername()) { InternalStars(emptySet()) }
            .get()
            .contains(application)
        setStar(actorAndProject, !isStarred, application)
    }

    suspend fun setStar(
        actorAndProject: ActorAndProject,
        isStarred: Boolean,
        application: String,
        flush: Boolean = true,
    ) {
        stars.computeIfAbsent(actorAndProject.actor.safeUsername()) { InternalStars(emptySet()) }
            .set(isStarred, application)
        if (flush) {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("starred", listOfNotNull(if (isStarred) application else null))
                        setParameter("not_starred", listOfNotNull(if (!isStarred) application else null))
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                        with
                            starred as (
                                select unnest(:starred::text[]) as app_to_star
                            ),
                            not_starred as (
                                select unnest(:not_starred::text[]) as app_to_unstar
                            ),
                            deleted as (
                                delete from app_store.favorited_by f
                                using not_starred ns
                                where
                                    f.application_name = ns.app_to_unstar
                                    and f.the_user = :username
                                returning f.application_name
                            ),
                            added as (
                                insert into app_store.favorited_by(the_user, application_name)
                                select :username, app_to_star
                                from starred
                                returning application_name
                            )
                        select application_name from deleted
                        union
                        select application_name from added
                    """
                )
            }
        }
    }
}