package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.AccountingV2
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.util.AsyncCache
import dk.sdu.cloud.accounting.util.MembershipStatusCacheEntry
import dk.sdu.cloud.accounting.util.ProjectCache
import dk.sdu.cloud.accounting.util.Providers
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
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max

// Deals with access to the application catalog

data class StoreFront(
    val carrousel: List<CarrouselItem>,
    val topPicks: List<TopPick>,
    val categories: List<ApplicationCategory>,
    val spotlight: Spotlight?,
    val knownApplication: Set<String>,
    val knownTools: Set<String>,
)

class Catalog(
    private val projectCache: ProjectCache,
    private val backgroundScope: BackgroundScope,
    private val serviceClient: AuthenticatedClient,
) {
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
            prepareApplicationsForUser(
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

        val apps = prepareApplicationsForUser(
            actorAndProject,
            appVersions.get().map { NameAndVersion(name, it) },
            withAllVersions = true,
            loadGroupApplications = loadGroupApplications
        )
        return apps.sortedByDescending { it.metadata.createdAt }
    }

    fun retrieveTool(
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

    fun listToolVersions(
        @Suppress("UNUSED_PARAMETER") actorAndProject: ActorAndProject,
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
            return copy(title = g.title, defaultApplicationToRun = g.defaultFlavor, logoHasText = g.logoHasText)
        } else {
            val a = retrieveApplication(ActorAndProject.System, applicationName ?: "", null) ?: return this
            val g = a.metadata.group?.metadata?.id?.let { groupId -> groups[groupId.toLong()]?.get() }
            return copy(
                title = a.metadata.title, defaultApplicationToRun = a.metadata.name,
                logoHasText = g?.logoHasText ?: false
            )
        }
    }

    private suspend fun Spotlight.prepare(): Spotlight {
        return copy(applications = applications.map { it.prepare() })
    }

    private fun CarrouselItem.prepare(): CarrouselItem {
        val defaultGroupApp = linkedGroup?.let { groups[it.toLong()]?.get()?.defaultFlavor }
        return copy(resolvedLinkedApp = defaultGroupApp ?: linkedApplication)
    }

    private val relevantStoreFrontsCache = AsyncCache<ActorAndProject, List<StoreFront>>(
        backgroundScope,
        timeToLiveMilliseconds = 10_000,
        timeoutMilliseconds = 1000,
        timeoutException = { throw RPCException("Failed to fetch application data", HttpStatusCode.InternalServerError) },
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

    private suspend fun findRelevantStoreFronts(actorAndProject: ActorAndProject): List<StoreFront> {
        return relevantStoreFrontsCache.retrieve(actorAndProject)
    }

    suspend fun retrieveLandingPage(actorAndProject: ActorAndProject): AppStore.RetrieveLandingPage.Response {
        val allStoreFronts = findRelevantStoreFronts(actorAndProject)

        val projectMembership = projectCache.lookup(actorAndProject.actor.safeUsername())

        val frontCategories = ArrayList<ApplicationCategory>()
        val frontSpotlight = spotlights.values.find { it.get().active }?.get()?.prepare()
        val newlyCreated = ArrayList<ApplicationSummaryWithFavorite>()
        val updated = ArrayList<ApplicationSummaryWithFavorite>()

        allStoreFronts.forEach { providerStoreFront ->
            val existingCategoryIds = frontCategories.map { it.metadata.id }
            for (category in providerStoreFront.categories) {
                if (category.metadata.id !in existingCategoryIds) {
                    frontCategories.add(category)
                }
            }

//            newlyCreated.addAll(providerStoreFront.newApplications.filter { it !in newlyCreated })
//            updated.addAll(providerStoreFront.recentlyUpdated.filter { it !in updated })
        }

        // NOTE(Dan): Only include categories which are non-empty after respecting the ACL.
        val filteredFrontCategories = frontCategories.filter { category ->
            val groups = category.status.groups ?: emptyList()
            groups.any { group ->
                val apps = group.status.applications ?: emptyList()
                apps.any { hasPermission(it.metadata.name, it.metadata.version, projectMembership) }
            }
        }

        return AppStore.RetrieveLandingPage.Response(
            carrousel.get(),
            topPicks.get(),
            filteredFrontCategories,
            frontSpotlight,
            newlyCreated,
            updated
        )
    }

    suspend fun retrieveCategory(
        actorAndProject: ActorAndProject,
        categoryId: Int,
        loadGroups: Boolean = false
    ): ApplicationCategory? {
        val projectMembership = projectCache.lookup(actorAndProject.actor.safeUsername())
        val storeFronts = findRelevantStoreFronts(actorAndProject)
        val allCategories = storeFronts
            .mapNotNull { front -> front.categories.find { it.metadata.id == categoryId } }

        val categoryMetadata = allCategories.firstOrNull() ?: return null
        val dedupedGroups = allCategories
            .asSequence()
            .flatMap { it.status.groups ?: emptyList() }
            .associateBy { it.metadata.id }
            .values
            .mapNotNull { prepareGroupForUser(actorAndProject, projectMembership, it) }
            .sortedBy { it.specification.title.lowercase() }


        return ApplicationCategory(
            categoryMetadata.metadata,
            categoryMetadata.specification,
            ApplicationCategory.Status(dedupedGroups)
        )
    }

    private fun prepareGroupForUser(
        actorAndProject: ActorAndProject,
        projectMembership: MembershipStatusCacheEntry,
        group: ApplicationGroup,
    ): ApplicationGroup? {
        val apps = group.status.applications ?: emptyList()
        val runnableApps = apps.filter { hasPermission(it.metadata.name, it.metadata.version, projectMembership) }
        if (runnableApps.isEmpty()) return null

        val newDefaultFlavor =
            if (runnableApps.size == 1) {
                runnableApps[0].metadata.name
            } else {
                val hasDefault = runnableApps.any { it.metadata.name == group.specification.defaultFlavor }
                if (hasDefault) {
                    group.specification.defaultFlavor
                } else {
                    null
                }
            }

        return group.copy(
            specification = group.specification.copy(
                defaultFlavor = newDefaultFlavor
            ),
            status = group.status.copy(
                applications = runnableApps
            )
        )
    }

    private suspend fun retrieveGroup(
        actorAndProject: ActorAndProject,
        groupId: Int,
        loadApplications: Boolean = false
    ): ApplicationGroup? {
        val group = groups[groupId.toLong()]?.toApiModel() ?: return null
        return group.copy(
            status = group.status.copy(
                applications =
                if (loadApplications) listApplicationsInGroup(actorAndProject, groupId).map { it.withoutInvocation() }
                else null,
            )
        )
    }

    suspend fun listApplicationsInGroup(
        actorAndProject: ActorAndProject,
        groupId: Int
    ): List<ApplicationWithFavoriteAndTags> {
        val group = groups[groupId.toLong()]?.get() ?: return emptyList()
        return prepareApplicationsForUser(actorAndProject, group.applications).sortedBy {
            it.metadata.flavorName?.lowercase() ?: it.metadata.name.lowercase()
        }
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

        val loaded = prepareApplicationsForUser(actorAndProject, candidates, withAllVersions = false)
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

        val loaded = prepareApplicationsForUser(actorAndProject, candidates, withAllVersions = false)
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
        return prepareApplicationsForUser(actorAndProject, candidates, withAllVersions = false)
    }

    private fun hasPermission(
        appName: String,
        username: String,
        projectMembership: MembershipStatusCacheEntry
    ): Boolean {
        val versions = applicationVersions[appName]?.get() ?: return false
        for (version in versions) {
            val app = applications[NameAndVersion(appName, version)] ?: continue
            if (app.metadata.public) return true
        }

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

    private suspend fun prepareApplicationsForUser(
        actorAndProject: ActorAndProject,
        versions: Collection<NameAndVersion>,
        withAllVersions: Boolean = false,
        loadGroupApplications: Boolean = false,
    ): List<ApplicationWithFavoriteAndTags> {
        val (actor) = actorAndProject
        if (actor.safeUsername().startsWith(AuthProviders.PROVIDER_PREFIX)) {
            return prepareApplicationsForProvider(actorAndProject, versions, withAllVersions, loadGroupApplications)
        }

        val result = ArrayList<Application>()
        val isPrivileged = isPrivileged(actorAndProject)
        val projectMembership = if (!isPrivileged) projectCache.lookup(actor.safeUsername()) else null

        val allStorefronts = findRelevantStoreFronts(actorAndProject)

        for (nameAndVersion in versions) {
            val app = applications[nameAndVersion] ?: continue
            if (!allStorefronts.any { nameAndVersion.name in it.knownApplication }) continue

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

    private suspend fun prepareApplicationsForProvider(
        actorAndProject: ActorAndProject,
        versions: Collection<NameAndVersion>,
        withAllVersions: Boolean = false,
        loadGroupApplications: Boolean = false,
    ): List<ApplicationWithFavoriteAndTags> {
        TODO()
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
                val backend = it.invocation.tool.tool?.description?.backend
                if (backend != null) {
                    providerCanRun(providerId, backend)
                } else {
                    false
                }
            }

            val supportedGroupIds = supportedApplications
                .mapNotNull { it.metadata.group?.metadata?.id }

            val supportedGroups = groups
                .filter { supportedGroupIds.contains(it.key.toInt()) }

            val supportedCategoryIds = supportedGroups
                .flatMap { it.value.get().categories }
                .toSet()

            val supportedCategories = categories
                .filter { it.key.toInt() in supportedCategoryIds }
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

            val resolvedCategories = supportedCategories.map { category ->
                category.copy(
                    status = ApplicationCategory.Status(
                        groups = supportedGroups.values
                            .filter { category.metadata.id in it.get().categories }
                            .map { it.toApiModel() }
                            .map { apiGroup ->
                                val applicationsInGroup = supportedApplications
                                    .filter { it.metadata.group?.metadata?.id == apiGroup.metadata.id }
                                    .map { ApplicationSummaryWithFavorite(it.metadata, false, emptyList()) }
                                    .groupBy { it.metadata.name }

                                val dedupedApplications = applicationsInGroup.map { (name, versions) ->
                                    versions.maxBy { it.metadata.createdAt }
                                }

                                apiGroup.copy(
                                    status = apiGroup.status.copy(
                                        applications = dedupedApplications,
                                    )
                                )
                            }
                    )
                )
            }

            val frontPicks = topPicks.get().map { it.prepare() }
            val frontCarrousel = carrousel.get().map { it.prepare() }
            val frontSpotlight = spotlights.values.find { it.get().active }?.get()?.prepare()

            val frontNewlyCreated = ArrayList<String>()
            val frontUpdated = ArrayList<NameAndVersion>()
            for (i in (newUpdates.size() - 1).downTo(0)) {
                frontUpdated.add(newUpdates.get(i))
            }

            for (i in (newApplications.size() - 1).downTo(0)) {
                frontNewlyCreated.add(newApplications.get(i))
            }


            val knownApplications = supportedApplications.map { it.metadata.name }.toSet()
            val knownTools = supportedApplications.map { it.invocation.tool.name }.toSet()

            StoreFront(
                frontCarrousel,
                frontPicks,
                resolvedCategories,
                frontSpotlight,
                knownApplications,
                knownTools,
            )
        }
    )

    // TODO(Brian): Temporary function. Will be replaced.
    private fun providerCanRun(providerId: String, backend: ToolBackend): Boolean {
        if (providerId in setOf("go-slurm", "slurm")) {
            return backend == ToolBackend.NATIVE
        } else if (providerId == "k8") {
            return backend == ToolBackend.DOCKER
        }
        return false
    }
}