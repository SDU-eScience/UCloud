package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.util.CyclicArray
import dk.sdu.cloud.accounting.util.IProjectCache
import dk.sdu.cloud.accounting.util.MembershipStatusCacheEntry
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.checkSingleLine
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.LookupProjectAndGroupRequest
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.DiscardingDBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.toTimestamp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import org.cliffc.high_scale_lib.NonBlockingHashMap
import org.cliffc.high_scale_lib.NonBlockingHashMapLong
import org.imgscalr.Scalr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max

// Introduction
// =====================================================================================================================
// Applications in UCloud are the key abstraction used for users to perform a unit of execution. An application
// describes the software package that will run the actual job. An application is an abstract concept which depend
// heavily on the service provider itself. This idea is best described through a number of examples:
//
// An application can be:
//
// - A container image + description of the command to run
// - A virtual machine base image (e.g. Ubuntu 22.04) + description of how to run it
// - A pre-installed application + description of the command to run
//
// As you can probably tell, an application tells the service provider how to load the appropriate software artifacts,
// and how to subsequently launch them with the user's input. Below is a short summary of the information contained in
// an application:
//
// - Metadata:      Every application is identified by a name and a version (see `NameAndVersion`)
// - Tool:          The tool is an abstraction which identifies the container image/VM base image/modules to load.
// - Parameters:    The parameters of an application is a description of the input parameters a user can supply
// - Invocation:    The invocation describes how to invoke the tool using the user input. For example, this can
//                  construct a specific command to run inside a container.
// - Control flags: Various control flags can change the behavior of applications, such as allowing certain resources
//                  to be attached to it (e.g. public links)
//
// Users can select applications from a catalog. The catalog uses a hierarchical structure for discovery:
//
// 1. Categories:   Categories are an overall selection of applications and typically describe a given field
//                  (e.g. Natural Sciences). They contain groups.
// 2. Groups:       Groups are collection of identical software, but in potentially differing configurations (e.g.
//                  VS Code for Java and VSCode for C++). Contains applications.
// 3. Applications: Applications describe a single piece of software in a specific application. Each application can
//                  have a flavor name (e.g. Java or C++)
class AppService(
    private val db: DBContext,
    private val projectCache: IProjectCache,
    private val serviceClient: AuthenticatedClient,
) {
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
    private val tools = NonBlockingHashMap<NameAndVersion, Tool>()
    private val toolVersions = NonBlockingHashMap<String, InternalVersions>()

    // Stores a lookup table of all apps and a mapping of all versions of a single app
    private val applications = NonBlockingHashMap<NameAndVersion, Application>()
    private val applicationVersions = NonBlockingHashMap<String, InternalVersions>()

    // Lookup tables for the hierarchical structuring of applications in the catalog
    private val groups = NonBlockingHashMapLong<InternalGroup>()
    private val categories = NonBlockingHashMapLong<InternalCategory>()

    // Access control lists allow ordinary users to access non-public applications
    private val accessControlLists = NonBlockingHashMap<String, InternalAcl>()

    // The landing page has various UI components which advertise specific applications. These lookup tables control
    // those widgets.
    private val spotlights = NonBlockingHashMapLong<InternalSpotlight>()
    private val topPicks = InternalTopPicks(emptyList())
    private val carrousel = InternalCarrousel(emptyList())

    // We track new applications and new updates (for the landing page). These are just ordinary cyclic arrays with a
    // small capacity. These are not thread-safe, so we use a mutex for the rare writes.
    private val appCreateMutex = Mutex()
    private val newApplications = CyclicArray<String>(5)
    private val newUpdates = CyclicArray<NameAndVersion>(5)

    // Users can mark an application with a star, which allows for easy access. This is done at the application level
    // but with no specific version. As a result, when you ask for your starred applications, you will get the most
    // recent version from looking up in applicationVersions[starred].
    private val stars = NonBlockingHashMap<String, InternalStars>()

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
                        ag.logo as group_logo
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

            appRows.forEach { row ->
                val app = row.toApplication()
                registerApplication(app, flush = false)
                val groupLogo = row.getAs<ByteArray?>("group_logo")
                val g = app.metadata.group
                if (g != null && groupLogo != null) {
                    groups[g.metadata.id.toLong()]?.updateMetadata(logo = groupLogo)
                }
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
                        select t.id, t.tag, t.priority
                        from app_store.categories t
                    """
                ).rows
                for (row in rawTagInfo) {
                    val tagId = row.getInt(0)!!
                    val tag = row.getString(1)!!
                    val priority = row.getInt(2)!!
                    categories.computeIfAbsent(tagId.toLong()) { InternalCategory(tag, emptySet(), priority) }
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
                    val id = row.getInt(0)!!
                    val title = row.getString(1)!!
                    val description = row.getString(2)!!
                    val active = row.getBoolean(3)!!
                    spotlights[id.toLong()] = InternalSpotlight(Spotlight(title, description, emptyList(), active, id))
                }

                session.sendPreparedStatement(
                    {},
                    """
                        select spotlight_id, application_name, group_id, description
                        from app_store.spotlight_items
                        order by spotlight_id, priority
                    """
                ).rows.forEach { row ->
                    val id = row.getInt(0)!!
                    val appName = row.getString(1)
                    val groupId = row.getInt(2)
                    val description = row.getString(3)!!

                    spotlights[id.toLong()]?.update {
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

                reorderCategories()
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

    private suspend fun registerApplication(inputApp: Application, flush: Boolean) {
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
        println("This is the new app: ${inputApp.metadata} ${app.metadata} ${previousApp?.metadata}")
        applications[key] = app

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
                    },
                    """
                        insert into app_store.applications
                            (name, version, application, created_at, modified_at, original_document, owner, tool_name, tool_version, authors, title, description, website, group_id, flavor_name, is_public) 
                        values 
                            (:id_name, :id_version, :application, to_timestamp(:created_at / 1000.0), to_timestamp(:modified_at / 1000.0), :original_document, :owner, :tool_name, :tool_version, :authors, :title, :description, :website, :group, :flavor, :is_public)
                    """
                )
            }
        }

        appCreateMutex.withLock {
            if (appVersions.get().size == 1) newApplications.add(app.metadata.name)
            newUpdates.add(NameAndVersion(app.metadata.name, app.metadata.version))
        }
    }

    private suspend fun registerTool(tool: Tool, flush: Boolean) {
        val key = tool.description.info
        if (tools[key] != null) {
            // NOTE(Dan): We purposefully don't deal with the race conflict we have here. I don't believe it is worth
            // complicating the rest of the code to deal with it. It seems extremely unlikely that anyone would need to
            // depend on this not potentially having a race condition.
            throw RPCException("This tool already exists!", HttpStatusCode.Conflict)
        }
        tools[key] = tool
        toolVersions.computeIfAbsent(key.name) { InternalVersions() }.add(key.version)

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
                    },
                    """
                        insert into app_store.tools
                            (name, version, created_at, modified_at, original_document, owner, tool) 
                        values 
                            (:name, :version, to_timestamp(:created_at / 1000.0), to_timestamp(:modified_at / 1000.0), :original_document, :owner, :tool)
                    """
                )
            }
        }
    }

    private fun registerGroup(group: ApplicationGroup): InternalGroup {
        return groups.computeIfAbsent(group.metadata.id.toLong()) {
            InternalGroup(
                group.specification.title,
                group.specification.description,
                group.specification.defaultFlavor,
                null,
                emptySet(),
                emptySet(),
            )
        }
    }

    private fun registerGroupTag(group: Int, tag: Int) {
        groups[group.toLong()]?.addCategories(setOf(tag))
        categories[tag.toLong()]?.addGroup(setOf(group))
    }

    // Catalog services (read)
    // =================================================================================================================
    // This section of the code contain function to retrieve by ID and list items by different criteria. This includes
    // functions for all parts of the catalog hierarchy. A small number of functions found in this section are
    // privileged due to them only being used for management purposes. For example, we do not allow normal users to list
    // all applications stored in the system. Instead, users must go through the hierarchy to find the applications they
    // want to use.
    suspend fun retrieveApplication(
        actorAndProject: ActorAndProject,
        name: String,
        version: String?,
        loadGroupApplications: Boolean = true,
    ): ApplicationWithFavoriteAndTags? {
        return if (version == null) {
            listVersions(actorAndProject, name, loadGroupApplications = loadGroupApplications).firstOrNull()
        } else {
            loadApplications(
                actorAndProject,
                listOf(NameAndVersion(name, version)),
                loadGroupApplications = loadGroupApplications
            ).singleOrNull()
        }
    }

    suspend fun listVersions(
        actorAndProject: ActorAndProject,
        name: String,
        loadGroupApplications: Boolean = false,
    ): List<ApplicationWithFavoriteAndTags> {
        val appVersions =
            applicationVersions[name] ?: throw RPCException("Unknown application: $name", HttpStatusCode.NotFound)

        val apps = loadApplications(
            actorAndProject,
            appVersions.get().map { NameAndVersion(name, it) },
            withAllVersions = true,
            loadGroupApplications = loadGroupApplications
        )
        return apps.sortedByDescending { it.metadata.createdAt }
    }

    suspend fun retrieveTool(
        actorAndProject: ActorAndProject,
        name: String,
        version: String?,
    ): Tool? {
        return if (version == null) {
            listToolVersions(actorAndProject, name).firstOrNull()
        } else {
            tools[NameAndVersion(name, version)]
        }
    }

    suspend fun listToolVersions(
        actorAndProject: ActorAndProject,
        name: String
    ): List<Tool> {
        val result = ArrayList<Tool>()
        val versions = toolVersions[name]?.get() ?: return emptyList()
        for (v in versions) {
            val key = NameAndVersion(name, v)
            result.add(tools[key] ?: continue)
        }
        return result.sortedByDescending { it.createdAt }
    }

    private suspend fun TopPick.prepare(): TopPick {
        val gid = groupId
        if (gid != null) {
            val g = groups[gid.toLong()]?.get() ?: return this
            return copy(title = g.title, defaultApplicationToRun = g.defaultFlavor)
        } else {
            val a = retrieveApplication(ActorAndProject.System, applicationName ?: "", null) ?: return this
            return copy(title = a.metadata.title, defaultApplicationToRun = a.metadata.name)
        }
    }

    private suspend fun Spotlight.prepare(): Spotlight {
        return copy(applications = applications.map { it.prepare() })
    }

    private suspend fun CarrouselItem.prepare(): CarrouselItem {
        val defaultGroupApp = linkedGroup?.let { groups[it.toLong()]?.get()?.defaultFlavor }
        return copy(resolvedLinkedApp = defaultGroupApp ?: linkedApplication)
    }

    suspend fun retrieveLandingPage(actorAndProject: ActorAndProject): AppStore.RetrieveLandingPage.Response {
        val picks = topPicks.get().map { it.prepare() }
        val categories = listCategories()
        val carrousel = carrousel.get().map { it.prepare() }
        val spotlight = spotlights.values.find { it.get().active }?.get()?.prepare()

        val newlyCreated = ArrayList<String>()
        val updated = ArrayList<NameAndVersion>()
        for (i in (newUpdates.size - 1).downTo(0)) {
            updated.add(newUpdates[i])
        }

        for (i in (newApplications.size - 1).downTo(0)) {
            newlyCreated.add(newApplications[i])
        }

        return AppStore.RetrieveLandingPage.Response(
            carrousel,
            picks,
            categories,
            spotlight,
            newlyCreated.mapNotNull {
                retrieveApplication(actorAndProject, it, null, loadGroupApplications = false)?.withoutInvocation()
            },
            updated.mapNotNull {
                retrieveApplication(
                    actorAndProject,
                    it.name,
                    it.version,
                    loadGroupApplications = false
                )?.withoutInvocation()
            }
        )
    }

    fun retrieveCarrouselImage(index: Int): ByteArray {
        return carrousel.getImages().getOrNull(index) ?: ByteArray(0)
    }

    fun listCategories(): List<ApplicationCategory> {
        return categories.toList().sortedBy { it.second.priority() }.map { (k, v) ->
            ApplicationCategory(
                ApplicationCategory.Metadata(
                    k.toInt(),
                ),
                ApplicationCategory.Specification(
                    v.title(),
                    "",
                )
            )
        }
    }

    suspend fun retrieveCategory(
        actorAndProject: ActorAndProject,
        categoryId: Int,
        loadGroups: Boolean = false
    ): ApplicationCategory? {
        val internal = categories[categoryId.toLong()] ?: return null
        val title = internal.title()
        var status = ApplicationCategory.Status()

        if (loadGroups) {
            val groups = internal.groups().mapNotNull { retrieveGroup(actorAndProject, it) }
                .sortedBy { it.specification.title.lowercase() }
            status = status.copy(groups = groups)
        }

        return ApplicationCategory(
            ApplicationCategory.Metadata(categoryId),
            ApplicationCategory.Specification(title),
            status
        )
    }

    suspend fun retrieveGroup(
        actorAndProject: ActorAndProject,
        groupId: Int,
        loadApplications: Boolean = false
    ): ApplicationGroup? {
        val info = groups[groupId.toLong()]?.get() ?: return null
        return ApplicationGroup(
            ApplicationGroup.Metadata(
                groupId,
            ),
            ApplicationGroup.Specification(
                info.title,
                info.description ?: "",
                info.defaultFlavor,
                info.categories
            ),
            ApplicationGroup.Status(
                if (loadApplications) listApplicationsInGroup(actorAndProject, groupId).map { it.withoutInvocation() }
                else null,
            )
        )
    }

    fun retrieveGroupLogo(groupId: Int): ByteArray? {
        val g = groups[groupId.toLong()]?.get() ?: return null
        return g.logo
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

    suspend fun listApplicationsInCategory(
        actorAndProject: ActorAndProject,
        category: Int
    ): Pair<String, List<ApplicationWithFavoriteAndTags>> {
        val resolvedCategory =
            categories[category.toLong()] ?: throw RPCException("Unknown group", HttpStatusCode.NotFound)

        val title = resolvedCategory.title()
        val groupIds = resolvedCategory.groups()

        val candidates = HashSet<NameAndVersion>()

        for (groupId in groupIds) {
            val group = groups[groupId.toLong()]?.get() ?: continue
            candidates.addAll(group.applications)
        }

        return title to loadApplications(
            actorAndProject,
            candidates,
            withAllVersions = false
        ).sortedBy { it.metadata.flavorName?.lowercase() ?: it.metadata.title.lowercase() }
    }

    suspend fun listByExtension(
        actorAndProject: ActorAndProject,
        files: List<String>,
    ): List<ApplicationWithExtension> {
        val extensions = files.flatMap { file ->
            if (file.contains(".")) {
                listOf("." + file.substringAfterLast('.'))
            } else {
                buildList {
                    val name = file.substringAfterLast('/')
                    add(name.removeSuffix("/"))

                    if (file.endsWith("/")) {
                        val fixedName = file.removeSuffix("/").substringAfterLast("/")
                        add("$fixedName/")
                        add(fixedName)
                        add("/")
                    }
                }
            }
        }.toSet()

        val candidates = HashSet<NameAndVersion>()
        for ((_, app) in applications) {
            if (app.invocation.fileExtensions.any { it in extensions }) {
                candidates.add(NameAndVersion(app.metadata.name, app.metadata.version))
            }
        }

        val loaded = loadApplications(actorAndProject, candidates, withAllVersions = false)
        return loaded.map {
            val extsSupported = it.invocation.fileExtensions.filter { it in extensions }
            ApplicationWithExtension(it.metadata, extsSupported)
        }
    }

    suspend fun search(
        actorAndProject: ActorAndProject,
        query: String,
    ): List<ApplicationWithFavoriteAndTags> {
        // NOTE(Dan): This probably won't perform very well, but my quick tests tells me that it is probably
        // still better than what we currently have.
        val queryWords = query.split(" ")
        val scores = HashMap<String, Int>()
        val arr1 = IntArray(128)
        val arr2 = IntArray(128)

        val scoreCutoff = max(1, queryWords.minOf { it.length } / 2)

        for ((nameAndVersion, app) in applications) {
            if (nameAndVersion.name in scores) continue
            val titleWords = app.metadata.title.split(" ")
            val descriptionWords = app.metadata.description.split(" ")

            // We rank each result by a home-brew system of inverting the editing distance. We search through both the
            // title and the description. Each query word gets the best match it finds through both title and
            // description. We sum the score of each query word to give a total score for each application. The
            // applications with the highest scores returned to the user.
            var totalScore = 0
            for (q in queryWords) {
                var maxScorePerWord = 0
                for (t in titleWords) {
                    var score = 0
                    val distance = levenshteinDistance(q, t, arr1, arr2)
                    val inverted = q.length - distance
                    if (inverted > 0) score += inverted
                    if (inverted == q.length) score += inverted

                    maxScorePerWord = max(score, maxScorePerWord)
                }

                for (t in descriptionWords) {
                    var score = 0
                    val distance = levenshteinDistance(q, t, arr1, arr2)
                    val inverted = q.length - distance
                    if (inverted > 0) score += inverted
                    if (inverted == q.length) score += inverted

                    maxScorePerWord = max(score, maxScorePerWord)
                }

                totalScore += maxScorePerWord
            }

            scores[nameAndVersion.name] = totalScore
        }

        val candidates = ArrayList<NameAndVersion>()
        scores.entries.sortedByDescending { it.value }.take(50).mapNotNull {
            if (it.value < scoreCutoff) return@mapNotNull null
            val name = it.key
            val appVersions = applicationVersions[name]?.get() ?: return@mapNotNull null
            candidates.addAll(appVersions.map { NameAndVersion(name, it) })
        }

        val loaded = loadApplications(actorAndProject, candidates, withAllVersions = false)
        return loaded.sortedByDescending { scores[it.metadata.name] ?: 0 }
    }

    // Calculates the "editing" distance between two strings. This returns back a number representing how different
    // two strings are. The number will always be positive. Lower numbers indicated similar strings. Zero indicates
    // that the strings are identical. String comparisons are case-insensitive.
    private fun levenshteinDistance(a: String, b: String, arr1: IntArray, arr2: IntArray): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        if (arr1.size < a.length + 1 || arr2.size < a.length + 1) return max(a.length, b.length)

        var cost = arr1
        var newCost = arr2

        for (i in cost.indices) cost[i] = i
        Arrays.fill(newCost, 0)

        for (i in 1..b.length) {
            newCost[0] = i
            for (j in 1..a.length) {
                val costToInsert = cost[j] + 1
                val costToDelete = newCost[j - 1] + 1
                var costToReplace = cost[j - 1]
                if (a[j - 1].lowercaseChar() != b[i - 1].lowercaseChar()) costToReplace += 1
                newCost[j] = minOf(costToReplace, costToInsert, costToDelete)
            }

            val temp = cost
            cost = newCost
            newCost = temp
        }

        return cost[a.length]
    }


    suspend fun loadApplications(
        actorAndProject: ActorAndProject,
        versions: Collection<NameAndVersion>,
        withAllVersions: Boolean = false,
        loadGroupApplications: Boolean = false,
    ): List<ApplicationWithFavoriteAndTags> {
        val result = ArrayList<Application>()
        val (actor) = actorAndProject
        val isPrivileged = isPrivileged(actorAndProject)
        val projectMembership = if (!isPrivileged) projectCache.lookup(actor.safeUsername()) else null

        for (nameAndVersion in versions) {
            val app = applications[nameAndVersion] ?: continue
            if (!app.metadata.public && !isPrivileged) {
                val username = actor.safeUsername()
                if (projectMembership == null) continue
                if (!hasPermission(nameAndVersion.name, username, projectMembership)) continue
            }

            val toolKey = app.invocation.tool.let { NameAndVersion(it.name, it.version) }
            val tool = tools[toolKey] ?: continue
            val group = app.metadata.group?.metadata?.id?.let { id ->
                retrieveGroup(actorAndProject, id, loadGroupApplications)
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

        val myStars = stars[actorAndProject.actor.safeUsername()]?.get() ?: emptySet()
        return deduped.map {
            ApplicationWithFavoriteAndTags(
                it.metadata,
                it.invocation,
                it.metadata.name in myStars,
                emptyList(),
            )
        }
    }

    suspend fun listGroups(actorAndProject: ActorAndProject): List<ApplicationGroup> {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        return groups.mapNotNull { (groupId, ig) ->
            retrieveGroup(actorAndProject, groupId.toInt())
        }
    }

    fun listAllApplications(): List<NameAndVersion> {
        return applicationVersions.flatMap { (name, versions) -> versions.get().map { NameAndVersion(name, it) } }
    }

    fun listAllTools(): List<NameAndVersion> {
        return toolVersions.flatMap { (name, versions) -> versions.get().map { NameAndVersion(name, it) } }
    }

    suspend fun retrieveSpotlights(
        actorAndProject: ActorAndProject,
        id: Int,
    ): Spotlight? {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        return spotlights[id.toLong()]?.get()
    }

    suspend fun listSpotlights(
        actorAndProject: ActorAndProject,
    ): List<Spotlight> {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        return spotlights.values.map { it.get() }
    }

    // Starred applications
    // =================================================================================================================
    suspend fun toggleStar(
        actorAndProject: ActorAndProject,
        application: String,
    ) {
        stars.computeIfAbsent(actorAndProject.actor.safeUsername()) { InternalStars(emptySet()) }.toggle(application)
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

    suspend fun listStarredApplications(
        actorAndProject: ActorAndProject,
    ): List<ApplicationWithFavoriteAndTags> {
        val appNames = stars[actorAndProject.actor.safeUsername()]?.get() ?: return emptyList()
        val candidates = ArrayList<NameAndVersion>()
        for (name in appNames) {
            val versions = applicationVersions[name]?.get() ?: emptyList()
            for (version in versions) {
                candidates.add(NameAndVersion(name, version))
            }
        }
        return loadApplications(actorAndProject, candidates, withAllVersions = false)
    }

    // Utilities
    // =================================================================================================================
    private fun isPrivileged(actorAndProject: ActorAndProject): Boolean {
        val (actor) = actorAndProject
        return actor == Actor.System || ((actor as? Actor.User)?.principal?.role ?: Role.GUEST) in Roles.PRIVILEGED
    }

    // Catalog services (write)
    // =================================================================================================================
    // This section of the code contain function to update the application catalog. Almost all calls in this section
    // are privileged. These calls will commonly update both the in-memory version of data and the database
    // immediately. When new applications are made, the appCreateMutex must be used.
    suspend fun createApplication(actorAndProject: ActorAndProject, application: Application) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)

        registerApplication(application, flush = true)
    }

    suspend fun createTool(actorAndProject: ActorAndProject, tool: Tool) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        registerTool(tool, flush = true)
    }

    suspend fun updateGroup(
        actorAndProject: ActorAndProject,
        id: Int,
        newTitle: String? = null,
        newDescription: String? = null,
        newDefaultFlavor: String? = null,
        newLogo: ByteArray? = null,
    ) {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        val g = groups[id.toLong()] ?: throw RPCException("Unknown group: $id", HttpStatusCode.NotFound)

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
                },
                """
                    update app_store.application_groups g
                    set
                        title = coalesce(:newTitle::text, g.title),
                        description = coalesce(:newDescription::text, g.description),
                        default_name = coalesce(:flavor::text, g.default_name),
                        logo = coalesce(:logo::bytea, g.logo)
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

        g.updateMetadata(newTitle, normalizedNewDescription, newDefaultFlavor, resizedLogo)
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

            val currentGroup = app.metadata.group
            if (currentGroup != null) {
                groups[currentGroup.metadata.id.toLong()]?.removeApplications(setOf(key))
            }

            newGroup?.addApplications(setOf(key))

            applications[key] = app.copy(
                metadata = app.metadata.copy(
                    group = groupId?.let { retrieveGroup(actorAndProject, it) }
                )
            )
        }
    }

    suspend fun createGroup(
        actorAndProject: ActorAndProject,
        title: String,
    ): Int {
        if (!isPrivileged(actorAndProject)) throw RPCException("Forbidden", HttpStatusCode.Forbidden)
        val id = if (db == DiscardingDBContext) {
            groupIdAllocatorForTestsOnly.getAndIncrement()
        } else {
            db.withSession { session ->
                session.sendPreparedStatement(
                    { setParameter("title", title) },
                    """
                        insert into app_store.application_groups (title)
                        values (:title)
                        returning id
                    """
                ).rows.single().getInt(0)!!
            }
        }

        groups[id.toLong()] = InternalGroup(title, "", null, null, emptySet(), emptySet())
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
                additions.add(EntityWithPermission(change.entity, change.rights))
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
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appName", appName)
                    setParameter("flavor", newFlavorName)
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

    suspend fun retrieveAcl(actorAndProject: ActorAndProject, name: String): Collection<EntityWithPermission> {
        if (!isPrivileged(actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        return accessControlLists[name]?.get() ?: emptySet()
    }

    suspend fun retrieveDetailedAcl(
        actorAndProject: ActorAndProject,
        name: String
    ): Collection<DetailedEntityWithPermission> {
        val list = retrieveAcl(actorAndProject, name)
        return list.map { e ->
            val projectAndGroupLookup =
                if (!e.entity.project.isNullOrBlank() && !e.entity.group.isNullOrBlank()) {
                    ProjectGroups.lookupProjectAndGroup.call(
                        LookupProjectAndGroupRequest(e.entity.project!!, e.entity.group!!),
                        serviceClient,
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
                        e.entity.user,
                        null,
                        null
                    )
                },
                e.permission
            )
        }
    }

    private fun hasPermission(
        appName: String,
        username: String,
        projectMembership: MembershipStatusCacheEntry
    ): Boolean {
        val acl = accessControlLists[appName]?.get() ?: return false
        for (entry in acl) {
            val entity = entry.entity
            if (entity.user == username) return true
            if (entity.group != null) {
                if (projectMembership.groupMemberOf.any { it.group == entity.group }) return true
            }

            if (entity.project != null) {
                if (projectMembership.adminInProjects.contains(entity.project)) return true
            }
        }
        return false
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
        val id = db.withSession { session ->
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
            activateSpotlight(actorAndProject, id)
        }

        return id
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

        val id = if (db == DiscardingDBContext) {
            groupIdAllocatorForTestsOnly.getAndIncrement()
        } else {
            db.withSession { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("title", specification.title)
                        setParameter("priority", priority)
                    },
                    """
                        insert into app_store.categories (tag, priority) 
                        values (:title, :priority)
                        returning id
                    """
                ).rows.single().getInt(0)!!
            }
        }

        val category = InternalCategory(specification.title, emptySet(), priority)
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

    // Internal storage classes
    // =================================================================================================================
    private class InternalVersions {
        private val listOfVersions = AtomicReference<List<String>>(emptyList())
        fun get(): List<String> = listOfVersions.get()

        fun add(version: String) {
            while (true) {
                val old = get()
                val new = old + version
                if (listOfVersions.compareAndSet(old, new)) break
            }
        }
    }

    private class InternalGroup(
        title: String,
        description: String,
        defaultFlavor: String?,
        logo: ByteArray?,
        applications: Set<NameAndVersion>,
        categories: Set<Int>,
    ) {
        data class GroupDescription(
            val title: String,
            val description: String,
            val defaultFlavor: String?,
            @Suppress("ArrayInDataClass") val logo: ByteArray?,
            val applications: Set<NameAndVersion>,
            val categories: Set<Int>,
        )

        private val ref = AtomicReference(
            GroupDescription(
                title,
                description,
                defaultFlavor,
                logo,
                applications,
                categories
            )
        )

        fun get() = ref.get()!!

        fun updateMetadata(
            title: String? = null,
            description: String? = null,
            defaultFlavor: String? = null,
            logo: ByteArray? = null,
        ) {
            while (true) {
                val oldRef = ref.get()
                val newRef = oldRef.copy(
                    title = title ?: oldRef.title,
                    description = description ?: oldRef.description,
                    defaultFlavor = defaultFlavor ?: oldRef.defaultFlavor,
                    logo = if (logo?.size == 0) null else logo ?: oldRef.logo
                )
                if (ref.compareAndSet(oldRef, newRef)) break
            }
        }

        fun addApplications(applications: Set<NameAndVersion>) {
            while (true) {
                val oldRef = ref.get()
                val newRef = oldRef.copy(
                    applications = oldRef.applications + applications
                )
                if (ref.compareAndSet(oldRef, newRef)) break
            }
        }

        fun removeApplications(applications: Set<NameAndVersion>) {
            while (true) {
                val oldRef = ref.get()
                val newRef = oldRef.copy(
                    applications = oldRef.applications - applications
                )
                if (ref.compareAndSet(oldRef, newRef)) break
            }
        }

        fun addCategories(categoryIds: Set<Int>) {
            while (true) {
                val oldRef = ref.get()
                val newRef = oldRef.copy(
                    categories = oldRef.categories + categoryIds
                )
                if (ref.compareAndSet(oldRef, newRef)) break
            }
        }

        fun removeCategories(categoryIds: Set<Int>) {
            while (true) {
                val oldRef = ref.get()
                val newRef = oldRef.copy(
                    categories = oldRef.categories - categoryIds
                )
                if (ref.compareAndSet(oldRef, newRef)) break
            }
        }
    }

    class InternalAcl(acl: Set<EntityWithPermission>) {
        private val acl = AtomicReference(acl)

        fun get(): Set<EntityWithPermission> {
            return acl.get()
        }

        fun addEntries(entries: Set<EntityWithPermission>) {
            while (true) {
                val old = acl.get()
                val new = old + entries
                if (acl.compareAndSet(old, new)) break
            }
        }

        fun removeEntries(entries: Set<AccessEntity>) {
            while (true) {
                val old = acl.get()
                val new = old.filter { it.entity !in entries }.toSet()
                if (acl.compareAndSet(old, new)) break
            }
        }
    }

    class InternalCategory(
        title: String,
        groups: Set<Int>,
        priority: Int,
    ) {
        private val title = AtomicReference(title)
        private val groups = AtomicReference(groups)
        private val priority = AtomicInteger(priority)

        fun updateTitle(newTitle: String) {
            title.set(newTitle)
        }

        fun addGroup(set: Set<Int>) {
            while (true) {
                val old = groups.get()
                val new = old + set
                if (groups.compareAndSet(old, new)) break
            }
        }

        fun removeGroup(set: Set<Int>) {
            while (true) {
                val old = groups.get()
                val new = old - set
                if (groups.compareAndSet(old, new)) break
            }
        }

        fun updatePriority(value: Int) {
            priority.set(value)
        }

        fun title() = title.get()
        fun groups() = groups.get()
        fun priority() = priority.get()
    }

    private class InternalSpotlight(spotlight: Spotlight) {
        private val ref = AtomicReference(spotlight)

        fun get() = ref.get()

        fun update(transform: (Spotlight) -> Spotlight) {
            while (true) {
                val old = ref.get()
                val new = transform(old)
                if (ref.compareAndSet(old, new)) break
            }
        }
    }

    private class InternalTopPicks(topPicks: List<TopPick>) {
        private val ref = AtomicReference(topPicks)

        fun get() = ref.get()

        fun update(transform: (List<TopPick>) -> List<TopPick>) {
            while (true) {
                val old = ref.get()
                val new = transform(old)
                if (ref.compareAndSet(old, new)) break
            }
        }
    }

    private class InternalCarrousel(items: List<CarrouselItem>) {
        private val ref = AtomicReference(items)
        private val images = AtomicReference<List<ByteArray>>(emptyList())

        fun get() = ref.get()
        fun getImages() = images.get()

        fun update(transform: (List<CarrouselItem>) -> List<CarrouselItem>) {
            while (true) {
                val old = ref.get()
                val new = transform(old)
                if (ref.compareAndSet(old, new)) break
            }
        }

        fun updateImages(transform: (List<ByteArray>) -> List<ByteArray>) {
            while (true) {
                val old = images.get()
                val new = transform(old)
                if (images.compareAndSet(old, new)) break
            }
        }
    }

    class InternalStars(stars: Set<String>) {
        private val stars = AtomicReference(stars)

        fun toggle(application: String) {
            while (true) {
                val old = stars.get()
                val new = if (application in old) {
                    old - application
                } else {
                    old + application
                }

                if (stars.compareAndSet(old, new)) break
            }
        }

        fun set(isStarred: Boolean, application: String) {
            while (true) {
                val old = stars.get()
                val new = if (isStarred) {
                    old + application
                } else {
                    old - application
                }

                if (stars.compareAndSet(old, new)) break
            }
        }

        fun get() = stars.get()!!
    }

    companion object {
        private val groupIdAllocatorForTestsOnly = AtomicInteger(0)
        private const val DESIRED_LOGO_WIDTH = 300
    }
}
