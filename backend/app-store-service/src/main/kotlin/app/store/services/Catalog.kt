package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.AccountingV2
import dk.sdu.cloud.accounting.api.ProductReferenceV2
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.util.AsyncCache
import dk.sdu.cloud.accounting.util.CyclicArray
import dk.sdu.cloud.accounting.util.MembershipStatusCacheEntry
import dk.sdu.cloud.accounting.util.ProjectCache
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

data class StoreFront(
    val providerId: String,

    val categories: Set<Long>,
    val groups: Set<Long>,
    val knownApplication: Set<String>,

    val newApplications: CyclicArray<NameAndVersion>,
    val updatedApplications: CyclicArray<NameAndVersion>,

    val publicCategories: Set<Long>,
    val publicGroups: Set<Long>,
)

class Catalog(
    private val projectCache: ProjectCache,
    private val backgroundScope: BackgroundScope,
    private val serviceClient: AuthenticatedClient,
    private val db: DBContext,
) {
    init {
        cacheInvalidationListeners.add {
            storeFronts.invalidateAll()
            availableStoreFronts.invalidateAll()
        }
    }

    /**
     * Retrieves an application by name and version. If the version is unspecified, then fetch the newest version.
     *
     * If the actor is an end-user then only applications usable from within the workspace will be returned. In addition,
     * only applications for which the end-user has permissions will be returned. UCloud administrators can see any
     * application in the system.
     *
     * If the actor is a provider then neither the "can run" check is performed or permission checks.
     */
    suspend fun retrieveApplication(
        actorAndProject: ActorAndProject,
        name: String,
        version: String?,
        flags: ApplicationFlags,
        discovery: WithCatalogDiscovery,
    ): Application? {
        val (actor) = actorAndProject
        val skipCanRunCheck = actor == Actor.System || actor.safeUsername().startsWith(AuthProviders.PROVIDER_PREFIX)
        if (!skipCanRunCheck) {
            val allStoreFronts = findRelevantStoreFronts(actorAndProject, discovery)
            if (!allStoreFronts.any { name in it.knownApplication }) return null
        }

        val appVersions = applicationVersions[name]?.get() ?: return null
        if (version == null) {
            for (actualVersion in appVersions.asReversed()) {
                val app = applications[NameAndVersion(name, actualVersion)] ?: continue
                val result = prepareApplicationForUser(
                    actorAndProject,
                    projectCache.lookup(actorAndProject.actor.safeUsername()),
                    app,
                    flags,
                    discovery,
                )

                if (result != null) return result
            }
            return null
        } else {
            val actualVersion = appVersions.find { it == version } ?: return null
            val app = applications[NameAndVersion(name, actualVersion)] ?: return null
            return prepareApplicationForUser(
                actorAndProject,
                projectCache.lookup(actorAndProject.actor.safeUsername()),
                app,
                flags,
                discovery,
            )
        }
    }

    /**
     * Retrieves an application group by its ID.
     *
     * This API is only available for end-users. Similar to the [retrieveApplication] call, this function will filter
     * out applications that are actually runnable in the current workspace. Groups which do not have any runnable
     * applications are automatically skipped. The default flavor of the group is automatically adjusted based on the
     * runnable applications.
     *
     * Groups will automatically include all applications across all service providers that are relevant in the current
     * workspace.
     */
    suspend fun retrieveGroup(
        actorAndProject: ActorAndProject,
        id: Long,
        flags: ApplicationFlags,
        discovery: WithCatalogDiscovery,
    ): ApplicationGroup? {
        val allStoreFronts = findRelevantStoreFronts(actorAndProject, discovery)
        if (!allStoreFronts.any { id in it.groups }) return null

        val group = groups[id] ?: return null

        val groupInfo = group.get()
        val apps = ArrayList<Application>()
        val appNames = groupInfo.applications.map { it.name }.toSet()
        for (appName in appNames) {
            val app = retrieveApplication(actorAndProject, appName, null, flags, discovery)
                ?: continue

            apps.add(app)
        }

        var defaultFlavor = groupInfo.defaultFlavor
        if (apps.size == 1) {
            defaultFlavor = apps.single().metadata.name
        } else if (defaultFlavor != null) {
            defaultFlavor = defaultFlavor.takeIf { flavor -> apps.any { it.metadata.name == flavor } }
        }

        if (apps.isEmpty()) return null
        return ApplicationGroup(
            metadata = ApplicationGroup.Metadata(
                group.id.toInt(),
            ),
            specification = ApplicationGroup.Specification(
                groupInfo.title,
                groupInfo.description,
                defaultFlavor,
                groupInfo.categories,
                ApplicationGroup.ColorReplacements(
                    groupInfo.colorRemappingLight,
                    groupInfo.colorRemappingDark
                ),
                groupInfo.logoHasText,
                groupInfo.curator,
            ),
            status = ApplicationGroup.Status(
                applications = if (flags.includeApplications) apps else null
            )
        )
    }

    suspend fun findGroupByApplication(
        actorAndProject: ActorAndProject,
        appName: String,
        appVersion: String?,
        flags: ApplicationFlags,
        discovery: WithCatalogDiscovery,
    ): ApplicationGroup? {
        val app = retrieveApplication(actorAndProject, appName, appVersion, flags, discovery) ?: return null
        val groupId = app.metadata.groupId
        val group = if (groupId != null) {
            retrieveGroup(actorAndProject, groupId, flags, discovery) ?: return null
        } else {
            ApplicationGroup(
                ApplicationGroup.Metadata(Int.MAX_VALUE),
                ApplicationGroup.Specification(
                    app.metadata.title,
                    app.metadata.description,
                    app.metadata.name,
                ),
                ApplicationGroup.Status(
                    applications = listOf(app),
                )
            )
        }

        var groupApps = group.status.applications
        if (groupApps != null) {
            groupApps = groupApps.filter { it.metadata.name != appName } + app
        }

        return group.copy(
            status = group.status.copy(
                applications = groupApps,
            )
        )
    }

    /**
     * Retrieves an application category by its ID.
     *
     * This API is only available for end-users. Similar to the [retrieveGroup] call, this function will filter in groups
     * based on the group's applications ability to be run in the current workspace. If a category is empty after this
     * filter, then they are not included.
     */
    suspend fun retrieveCategory(
        actorAndProject: ActorAndProject,
        id: Long,
        flags: ApplicationFlags,
        discovery: WithCatalogDiscovery,
    ): ApplicationCategory? {
        val allStoreFronts = findRelevantStoreFronts(actorAndProject, discovery)
        if (!allStoreFronts.any { id in it.categories }) return null

        val category = categories[id]
        val groupsIds = category.groups()
        val groups = ArrayList<ApplicationGroup>()

        // NOTE(Dan): This will skip group and app loading if we know the category is public
        if (flags.includeGroups || allStoreFronts.none { id in it.publicCategories }) {
            for (groupId in groupsIds) {
                val group = retrieveGroup(actorAndProject, groupId.toLong(), flags, discovery)
                    ?: continue

                groups.add(group)
            }

            groups.sortBy { it.specification.title.lowercase() }

            if (groups.isEmpty()) return null
        }

        return ApplicationCategory(
            ApplicationCategory.Metadata(id.toInt()),
            ApplicationCategory.Specification(
                category.title(),
                "",
                category.curator(),
            ),
            ApplicationCategory.Status(
                if (flags.includeGroups) groups else null
            )
        )
    }

    private suspend fun prepareTopPickForUser(
        actorAndProject: ActorAndProject,
        item: TopPick,
        discovery: WithCatalogDiscovery
    ): TopPick? {
        val groupId = item.groupId
        val appName = item.applicationName
        if (groupId != null) {
            val group = retrieveGroup(actorAndProject, groupId.toLong(), ApplicationFlags(), discovery)
                ?: return null

            return item.copy(
                title = group.specification.title,
                applicationName = null,
                groupId = groupId,
                defaultApplicationToRun = group.specification.defaultFlavor,
            )
        } else if (appName != null) {
            val app = retrieveApplication(actorAndProject, appName, null, ApplicationFlags(), discovery)
                ?: return null

            return item.copy(
                title = app.metadata.title,
                applicationName = appName,
                groupId = null,
                defaultApplicationToRun = app.metadata.name,
            )
        } else {
            return null
        }
    }

    suspend fun retrieveLandingPage(
        actorAndProject: ActorAndProject,
        discovery: WithCatalogDiscovery,
    ): AppStore.RetrieveLandingPage.Response {
        val allStoreFronts = findRelevantStoreFronts(actorAndProject, discovery)
        val allCategories = allStoreFronts.flatMap { it.categories }.toSet()

        val resolvedCategories = allCategories
            .mapNotNull { retrieveCategory(actorAndProject, it, ApplicationFlags(), discovery) }
            .sortedBy { categories[it.metadata.id.toLong()]?.priority() ?: 10000 }

        val slides = carrousel.get().map { slide ->
            var resolvedLinkedApp: String? = null

            var applicationName = slide.linkedApplication
            var groupId = slide.linkedGroup?.toLong()
            if (groupId != null) {
                val group = retrieveGroup(actorAndProject, groupId, ApplicationFlags(), discovery)
                if (group == null) {
                    groupId = null
                } else {
                    resolvedLinkedApp = group.specification.defaultFlavor
                }
            } else if (applicationName != null) {
                val app = retrieveApplication(actorAndProject, applicationName, null, ApplicationFlags(), discovery)
                if (app == null) {
                    applicationName = null
                }
            }

            slide.copy(
                linkedApplication = applicationName,
                linkedGroup = groupId?.toInt(),
                resolvedLinkedApp = resolvedLinkedApp,
            )
        }

        var picks = topPicks.get().mapNotNull { prepareTopPickForUser(actorAndProject, it, discovery) }
        if (picks.size < 5) {
            // Try to extract applications from the different storefronts, assuming that they don't have a lot of
            // applications. For some providers, we can easily just display the entire catalog on the landing page.

            val groups = TreeSet<ApplicationGroup> { o1, o2 ->
                o1.specification.title.compareTo(o2.specification.title)
            }

            for (pick in picks) {
                val groupId = pick.groupId ?: continue
                val group = retrieveGroup(actorAndProject, groupId.toLong(), ApplicationFlags(), discovery) ?: continue
                groups.add(group)
            }

            outer@ for (front in allStoreFronts) {
                for (groupId in front.groups) {
                    val group = retrieveGroup(actorAndProject, groupId, ApplicationFlags(), discovery) ?: continue
                    groups.add(group)

                    if (groups.size >= 15) break@outer
                }
            }

            picks = groups.map { group ->
                TopPick(
                    group.specification.title,
                    null,
                    group.metadata.id.toInt(),
                    group.specification.description,
                    group.specification.defaultFlavor,
                    group.specification.logoHasText,
                )
            }
        }

        val activeSpotlight = spotlights.values.find { it.get().active }?.get()?.let { spotlight ->
            val newApps = spotlight.applications.mapNotNull { prepareTopPickForUser(actorAndProject, it, discovery) }
            spotlight.copy(applications = newApps).takeIf { it.applications.isNotEmpty() }
        }

        val allNewApplications = ArrayList<Application>()
        val allUpdatedApplications = ArrayList<Application>()

        for (storeFront in allStoreFronts) {
            for (newApp in storeFront.newApplications) {
                val element =
                    retrieveApplication(actorAndProject, newApp.name, newApp.version, ApplicationFlags(), discovery)
                        ?: continue

                allNewApplications.add(element)
            }

            for (updatedApp in storeFront.updatedApplications) {
                val element =
                    retrieveApplication(
                        actorAndProject,
                        updatedApp.name,
                        updatedApp.version,
                        ApplicationFlags(),
                        discovery
                    ) ?: continue

                allUpdatedApplications.add(element)
            }
        }

        allNewApplications.sortByDescending { it.metadata.createdAt }
        allUpdatedApplications.sortByDescending { it.metadata.createdAt }

        return AppStore.RetrieveLandingPage.Response(
            slides,
            picks,
            resolvedCategories,
            activeSpotlight,
            allNewApplications.take(5),
            allUpdatedApplications.take(5),

            // NOTE(Dan): The curator status is filled in by the Studio but kept in this call to save the frontend a
            // round-trip.
            emptyList(),

            availableProviders = availableStoreFronts.retrieve(actorAndProject).map { it.providerId }.toSet()
        )
    }

    /**
     * Same semantics as [retrieveApplication] but searches for a specific application
     */
    suspend fun search(
        actorAndProject: ActorAndProject,
        query: String,
        discovery: WithCatalogDiscovery,
    ): List<Application> {
        val relevantStoreFronts = findRelevantStoreFronts(actorAndProject, discovery)
        val queryProfile = searchCosine.getProfile(query.lowercase())
        val similarities = HashMap<Long, Double>()

        for (storeFront in relevantStoreFronts) {
            for (groupId in storeFront.groups) {
                if (groupId in similarities) continue
                val group = groups[groupId] ?: continue
                val groupInfo = group.get()

                var similarity = searchCosine.safeSimilarity(queryProfile, groupInfo.titleSearchShingle)
                similarity += searchCosine.safeSimilarity(queryProfile, groupInfo.descriptionSearchShingle) * 0.5
                similarities[groupId] = similarity
            }
        }

        val resolvedApps = HashMap<NameAndVersion, Application>()
        val result = HashMap<NameAndVersion, Double>()
        val candidates = similarities.entries.sortedByDescending { it.value }
        for ((groupId, similarity) in candidates) {
            val group = retrieveGroup(
                actorAndProject,
                groupId,
                ApplicationFlags(
                    includeApplications = true,
                    includeStars = true
                ),
                discovery,
            ) ?: continue

            val apps = group.status.applications ?: emptyList()
            for (app in apps) {
                val profile = searchCosine.getProfile(app.metadata.title.lowercase())
                val ownSimilarity = searchCosine.safeSimilarity(queryProfile, profile)
                val nameAndVersion = NameAndVersion(app.metadata.name, app.metadata.version)
                result[nameAndVersion] = similarity + ownSimilarity
                resolvedApps[nameAndVersion] = app
            }

            if (result.size >= 25) break
        }

        val sortedByDescending = result.entries.sortedByDescending { it.value }
        val bestScore = sortedByDescending.getOrNull(0)?.value ?: 1.0
        return sortedByDescending.filter { it.value / bestScore >= 0.1 }.mapNotNull { resolvedApps[it.key] }
    }

    suspend fun openWithApplication(
        actorAndProject: ActorAndProject,
        files: List<String>,
    ): List<Application> {
        // NOTE(Dan): Open with recommendations should always just pick from what we can actually run, it makes no sense
        // to show other applications.
        val discovery = CatalogDiscovery(CatalogDiscoveryMode.AVAILABLE)

        val allStoreFronts = findRelevantStoreFronts(actorAndProject, discovery)

        val membership = projectCache.lookup(actorAndProject.actor.safeUsername())

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

        val candidates = HashSet<Application>()
        for (storeFront in allStoreFronts) {
            for (appName in storeFront.knownApplication) {
                val app = retrieveApplication(
                    actorAndProject,
                    appName,
                    null,
                    ApplicationFlags(includeInvocation = true),
                    discovery
                ) ?: continue

                if (app.invocation!!.fileExtensions.any { it in extensions }) {
                    candidates.add(app)
                }
            }
        }

        val dedupedCandidates = candidates.groupBy { it.metadata.name }.map { (name, versions) ->
            versions.sortedByDescending { it.metadata.createdAt }[0]
        }

        val result = ArrayList<Application>()
        for (candidate in dedupedCandidates) {
            val loaded = prepareApplicationForUser(
                actorAndProject,
                membership,
                candidate,
                ApplicationFlags(
                    includeVersions = false,
                    includeStars = true,
                    includeInvocation = true,
                ),
                discovery,
            ) ?: continue

            result.add(loaded)
        }

        result.sortBy { it.metadata.title }
        return result
    }

    suspend fun retrieveStarredApplications(
        actorAndProject: ActorAndProject,
        discovery: WithCatalogDiscovery,
    ): List<Application> {
        val myStars = stars[actorAndProject.actor.safeUsername()]?.get() ?: return emptyList()
        return myStars.mapNotNull {
            retrieveApplication(
                actorAndProject,
                it,
                null,
                ApplicationFlags(
                    includeStars = true,
                    includeInvocation = true
                ),
                discovery,
            )
        }
    }

    private val availableStoreFronts = AsyncCache<ActorAndProject, List<StoreFront>>(
        backgroundScope,
        timeToLiveMilliseconds = 120_000,
        timeoutMilliseconds = 10_000,
        timeoutException = {
            throw RPCException(
                "Failed to fetch application data",
                HttpStatusCode.InternalServerError
            )
        },
        retrieve = { actorAndProject ->
            val relevantProviders = AccountingV2.findRelevantProviders.call(
                bulkRequestOf(
                    AccountingV2.FindRelevantProviders.RequestItem(
                        actorAndProject.actor.safeUsername(),
                        actorAndProject.project,
                        useProject = true,
                        filterProductType = ProductType.COMPUTE,
                        includeFreeToUse = false,
                    )
                ),
                serviceClient
            ).orThrow()

            relevantProviders.responses.first().providers.map { providerId ->
                storeFronts.retrieve(providerId)
            }
        }
    )

    private val allProviders = AsyncCache<Unit, Set<String>>(
        backgroundScope,
        timeToLiveMilliseconds = 120_000,
        timeoutMilliseconds = 10_000,
        timeoutException = {
            throw RPCException(
                "Failed to fetch application data",
                HttpStatusCode.InternalServerError
            )
        },
        retrieve = { _ ->
            AccountingV2.findAllProviders.call(
                bulkRequestOf(
                    AccountingV2.FindAllProviders.RequestItem(
                        filterProductType = ProductType.COMPUTE,
                        includeFreeToUse = false,
                    )
                ),
                serviceClient
            ).orThrow().responses.flatMap { it.providers }.toSet()
        }
    )

    private suspend fun findRelevantStoreFronts(
        actorAndProject: ActorAndProject,
        discovery: WithCatalogDiscovery,
    ): List<StoreFront> {
        return when (discovery.discovery ?: CatalogDiscoveryMode.DEFAULT) {
            CatalogDiscoveryMode.ALL -> {
                listOf(storeFronts.retrieve(""))
            }

            CatalogDiscoveryMode.AVAILABLE -> {
                availableStoreFronts.retrieve(actorAndProject)
            }

            CatalogDiscoveryMode.SELECTED -> {
                val all = allProviders.retrieve(Unit)
                val selectedProviders = (discovery.selected ?: "").split(",").filter { it in all }
                selectedProviders.map { storeFronts.retrieve(it) }
            }
        }
    }

    private suspend fun prepareApplicationForUser(
        actorAndProject: ActorAndProject,
        membership: MembershipStatusCacheEntry,
        application: Application,
        flags: ApplicationFlags,
        discovery: WithCatalogDiscovery,
    ): Application? {
        if (!application.metadata.public) {
            if (!hasPermission(application.metadata.name, actorAndProject.actor, membership)) {
                return null
            }
        }

        val favorite: Boolean? = if (!flags.includeStars) {
            null
        } else {
            val myStars = stars[actorAndProject.actor.safeUsername()]
            if (myStars == null) {
                false
            } else {
                application.metadata.name in myStars.get()
            }
        }

        val invocation: ApplicationInvocationDescription? = if (!flags.includeInvocation) {
            null
        } else {
            application.invocation
        }

        var versions: List<String>? = null
        if (flags.includeVersions) {
            val allResolvedVersions = ArrayList<Application>()
            allResolvedVersions.add(application)

            val allVersions =
                applicationVersions[application.metadata.name]?.get() ?: listOf(application.metadata.version)

            for (version in allVersions) {
                if (version == application.metadata.version) continue

                val app = retrieveApplication(
                    actorAndProject,
                    application.metadata.name,
                    version,
                    ApplicationFlags(),
                    discovery,
                )

                if (app != null) allResolvedVersions.add(app)
            }

            allResolvedVersions.sortByDescending { it.metadata.createdAt }
            versions = allResolvedVersions.map { it.metadata.version }
        }

        return application.copy(
            favorite = favorite,
            invocation = invocation,
            versions = versions,
        )
    }

    private fun hasPermission(
        appName: String,
        actor: Actor,
        projectMembership: MembershipStatusCacheEntry
    ): Boolean {
        if (actor == Actor.System) return true
        if (actor.safeUsername().startsWith(AuthProviders.PROVIDER_PREFIX)) return true
        if (isPrivileged(actor)) return true

        val username = actor.safeUsername()
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

    private val storeFronts = AsyncCache<String, StoreFront>(
        backgroundScope,
        timeToLiveMilliseconds = 1000L * 60 * 30,
        timeoutException = {
            throw RPCException(
                "Failed to fetch information about allocations",
                HttpStatusCode.BadGateway
            )
        },
        retrieve = { providerId ->
            val supportedApplications = applications.values.filter {
                if (providerId == "") {
                    true
                } else {
                    val backend = it.invocation?.tool?.tool?.description?.backend
                    if (backend != null) {
                        providerCanRun(providerId, backend)
                    } else {
                        false
                    }
                }
            }

            val supportedGroupIds = supportedApplications
                .mapNotNull { it.metadata.groupId }
                .toSet()

            val supportedGroups = groups
                .filter { supportedGroupIds.contains(it.key) }

            val supportedCategoryIds = supportedGroups
                .flatMap { it.value.get().categories }
                .map { it.toLong() }
                .toSet()

            val newApplications = CyclicArray<NameAndVersion>(5)
            val updatedApplications = CyclicArray<NameAndVersion>(5)

            val knownApplications = supportedApplications.map { it.metadata.name }.toSet()

            val resolvedApplications = knownApplications.flatMap { appName ->
                val versions = applicationVersions[appName]?.get() ?: return@flatMap emptyList()
                versions.mapNotNull { applications[NameAndVersion(appName, it)] }
            }.sortedBy { it.metadata.createdAt }

            val publicGroups = HashSet<Long>()
            val publicCategories = HashSet<Long>()

            val seenBefore = HashSet<String>()
            for (app in resolvedApplications) {
                val key = NameAndVersion(app.metadata.name, app.metadata.version)
                updatedApplications.add(key)
                if (app.metadata.name !in seenBefore) {
                    newApplications.add(key)
                }

                if (app.metadata.public) {
                    val groupId = app.metadata.groupId
                    if (groupId != null) {
                        publicGroups.add(groupId)
                        val group = groups[groupId]!!

                        for (category in group.get().categories) {
                            publicCategories.add(category.toLong())
                        }
                    }
                }

                seenBefore.add(app.metadata.name)
            }

            StoreFront(
                providerId,

                supportedCategoryIds,
                supportedGroupIds,
                knownApplications,
                newApplications,
                updatedApplications,
                publicCategories,
                publicGroups,
            )
        }
    )

    private data class MachineSupport(
        val product: ProductReferenceV2,
        val backendType: ToolBackend,

        val vnc: Boolean,
        val logs: Boolean,
        val terminal: Boolean,
        val timeExtension: Boolean,
        val web: Boolean,
        val peers: Boolean,
        val suspend: Boolean,
    )

    private val supportCache = AsyncCache<Unit, List<MachineSupport>>(
        backgroundScope,
        timeToLiveMilliseconds = 60_000,
        timeoutException = {
            throw RPCException("Failed to fetch information about service providers", HttpStatusCode.BadGateway)
        },
        retrieve = { _ ->
            db.withSession(reason = "catalog supportCache") { session ->
                val rows = session.sendPreparedStatement(
                    {},
                    """
                        select
                            product_name,
                            product_category,
                            product_provider,
                            backend_type,
                            vnc,
                            logs,
                            terminal,
                            time_extension,
                            web,
                            peers,
                            suspend
                        from
                            app_orchestrator.machine_support_info
                    """
                ).rows

                rows.map { row ->
                    MachineSupport(
                        ProductReferenceV2(
                            row.getString(0)!!,
                            row.getString(1)!!,
                            row.getString(2)!!,
                        ),
                        ToolBackend.valueOf(row.getString(3)!!),
                        row.getBoolean(4)!!,
                        row.getBoolean(5)!!,
                        row.getBoolean(6)!!,
                        row.getBoolean(7)!!,
                        row.getBoolean(8)!!,
                        row.getBoolean(9)!!,
                        row.getBoolean(10)!!,
                    )
                }
            }
        }
    )

    private suspend fun providerCanRun(providerId: String, backend: ToolBackend): Boolean {
        val allSupport = supportCache.retrieve(Unit)
        return allSupport.any { support ->
            support.product.provider == providerId && support.backendType == backend
        }
    }
}
