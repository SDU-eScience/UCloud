package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.AccountingV2
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.util.AsyncCache
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
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

// Deals with access to the application catalog

data class StoreFront(
    val categories: Set<Long>,
    val groups: Set<Long>,
    val knownApplication: Set<String>,


    // TODO Keep track of categories and groups containing at least one public application
    //   Use these to bypass can run check.
)

class Catalog(
    private val projectCache: ProjectCache,
    private val backgroundScope: BackgroundScope,
    private val serviceClient: AuthenticatedClient,
) {
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
        flags: ApplicationFlags
    ): Application? {
        val (actor) = actorAndProject
        val skipCanRunCheck = actor == Actor.System || actor.safeUsername().startsWith(AuthProviders.PROVIDER_PREFIX)
        if (!skipCanRunCheck) {
            val allStoreFronts = findRelevantStoreFronts(actorAndProject)
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
        flags: ApplicationFlags
    ): ApplicationGroup? {
        val allStoreFronts = findRelevantStoreFronts(actorAndProject)
        if (!allStoreFronts.any { id in it.groups }) return null

        val group = groups[id] ?: return null

        val groupInfo = group.get()
        val apps = ArrayList<Application>()
        val appNames = groupInfo.applications.map { it.name }.toSet()
        for (appName in appNames) {
            val app = retrieveApplication(actorAndProject, appName, null, flags)
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
        flags: ApplicationFlags
    ): ApplicationGroup? {
        val app = retrieveApplication(actorAndProject, appName, appVersion, flags) ?: return null
        val groupId = app.metadata.groupId ?: return null
        val group = retrieveGroup(actorAndProject, groupId, flags) ?: return null

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
        flags: ApplicationFlags
    ): ApplicationCategory? {
        val allStoreFronts = findRelevantStoreFronts(actorAndProject)
        if (!allStoreFronts.any { id in it.categories }) return null

        val category = categories[id]
        val groupsIds = category.groups()
        val groups = ArrayList<ApplicationGroup>()

        for (groupId in groupsIds) {
            val group = retrieveGroup(actorAndProject, groupId.toLong(), flags)
                ?: continue

            groups.add(group)
        }

        groups.sortBy { it.specification.title.lowercase() }

        if (groups.isEmpty()) return null

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

    suspend fun retrieveLandingPage(
        actorAndProject: ActorAndProject,
    ): AppStore.RetrieveLandingPage.Response {
        val allStoreFronts = findRelevantStoreFronts(actorAndProject)
        val allCategories = allStoreFronts.flatMap { it.categories }.toSet()

        val resolvedCategories = allCategories
            .mapNotNull { retrieveCategory(actorAndProject, it, ApplicationFlags()) }
            .sortedBy { categories[it.metadata.id.toLong()]?.priority() ?: 10000 }

        return AppStore.RetrieveLandingPage.Response(
            emptyList(),
            emptyList(),
            resolvedCategories,
            null,
            emptyList(),
            emptyList(),
        )
    }

    /**
     * Same semantics as [retrieveApplication] but searches for a specific application
     */
    suspend fun search(
        actorAndProject: ActorAndProject,
        query: String,
    ): List<Application> = TODO()

    suspend fun openWithApplication(
        actorAndProject: ActorAndProject,
        files: List<String>,
    ): List<Application> = TODO()

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

    /*
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
     */

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

    private suspend fun prepareApplicationForUser(
        actorAndProject: ActorAndProject,
        membership: MembershipStatusCacheEntry,
        application: Application,
        flags: ApplicationFlags,
    ): Application? {
        if (!application.metadata.public) {
            if (!hasPermission(application.metadata.name, actorAndProject.actor, membership))  {
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
                    ApplicationFlags()
                )

                if (app != null) allResolvedVersions.add(app)
            }

            allResolvedVersions.sortBy { it.metadata.createdAt }
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
                val backend = it.invocation?.tool?.tool?.description?.backend
                if (backend != null) {
                    providerCanRun(providerId, backend)
                } else {
                    false
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

            val knownApplications = supportedApplications.map { it.metadata.name }.toSet()

            StoreFront(
                supportedCategoryIds,
                supportedGroupIds,
                knownApplications,
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