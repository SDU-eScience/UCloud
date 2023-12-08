package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.accounting.util.IIdCardService
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceDocumentUpdate
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.SimpleResourceIncludeFlags
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.ReadWriterMutex
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.cliffc.high_scale_lib.NonBlockingHashMapLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.*

fun Product.toReference(): ProductReference {
    return ProductReference(name, category.name, category.provider)
}

class FakeProductCache : IProductCache {
    @Volatile
    private var products = listOf<ProductV2>(
        ProductV2.Compute(
            name = "-",
            price = 1L,
            category = ProductCategory("-", "-", ProductType.COMPUTE, AccountingUnit("DKK", "DKK", true, false), AccountingFrequency.PERIODIC_MINUTE),
            description = "-"
        )
    )
    private val insertMutex = Mutex()

    suspend fun insert(product: ProductV2): Int {
        insertMutex.withLock {
            val newList = products + product
            products = newList
            return newList.size - 1
        }
    }

    suspend fun insert(name: String, category: String, provider: String): Int {
        return insert(
            ProductV2.Compute(
                name,
                1L,
                ProductCategory(
                    name,
                    provider,
                    ProductType.COMPUTE,
                    AccountingUnit(
                        name = "DKK",
                        namePlural = "DKK",
                        floatingPoint = true,
                        displayFrequencySuffix = false
                    ),
                    AccountingFrequency.PERIODIC_MINUTE
                ),
                description = "Test"
            )
        )
    }

    override suspend fun referenceToProductId(ref: ProductReference): Int? {
        val list = products
        for (i in list.indices) {
            val product = list[i]
            if (product.name == ref.id && product.category.name == ref.category && product.category.provider == ref.provider) {
                return i
            }
        }
        return -1
    }

    override suspend fun productIdToReference(id: Int): ProductReferenceV2? {
        val list = products
        if (id < 0 || id >= list.size) return null
        return list[id].toReference()
    }

    override suspend fun productIdToProduct(id: Int): ProductV2? {
        return products.getOrNull(id)
    }

    override suspend fun productNameToProductIds(name: String): List<Int>? {
        val result = ArrayList<Int>()
        val list = products
        for (i in list.indices) {
            if (list[i].name == name) result.add(i)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    override suspend fun productCategoryToProductIds(category: String): List<Int>? {
        val result = ArrayList<Int>()
        val list = products
        for (i in list.indices) {
            if (list[i].category.name == category) result.add(i)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    override suspend fun productProviderToProductIds(provider: String): List<Int>? {
        val result = ArrayList<Int>()
        val list = products
        for (i in list.indices) {
            if (list[i].category.provider == provider) result.add(i)
        }
        return result.takeIf { it.isNotEmpty() }
    }

    override suspend fun products(): List<ProductV2> {
        return products
    }
}

private fun ProductV2.toReference(): ProductReferenceV2? {
    return ProductReferenceV2(name, category.name, category.provider)
}

class FakeIdCardService(val products: FakeProductCache) : IIdCardService {
    private val users = ArrayList<String>().also { it.add("") }
    private val projects = ArrayList<String>().also { it.add("") }
    private val groups = ArrayList<Pair<Int, String>>().also { it.add(Pair(0, "")) }
    private val groupMembers = ArrayList<ArrayList<Int>>().also { it.add(ArrayList()) }
    private val admins = ArrayList<ArrayList<Int>>().also { it.add(ArrayList()) }
    private val mutex = ReadWriterMutex()

    suspend fun createUser(username: String): Int {
        return mutex.withWriter {
            users.add(username)
            users.size - 1
        }
    }

    suspend fun createProject(id: String): Int {
        return mutex.withWriter {
            projects.add(id)
            admins.add(ArrayList())
            projects.size - 1
        }
    }

    suspend fun createGroup(project: Int, title: String): Int {
        return mutex.withWriter {
            groups.add(Pair(project, title))
            groupMembers.add(ArrayList())
            groups.size - 1
        }
    }

    suspend fun addUserToGroup(uid: Int, gid: Int) {
        mutex.withWriter {
            groupMembers[gid].add(uid)
        }
    }

    suspend fun removeUserFromGroup(uid: Int, gid: Int) {
        mutex.withWriter {
            groupMembers[gid].remove(uid)
        }
    }

    suspend fun addAdminToProject(uid: Int, pid: Int) {
        mutex.withWriter {
            admins[pid].add(uid)
        }
    }

    suspend fun removeAdminFromProject(uid: Int, pid: Int) {
        mutex.withWriter {
            admins[pid].remove(uid)
        }
    }

    suspend fun fetchProvider(providerId: String): IdCard {
        return fetchIdCard(
            ActorAndProject(
                Actor.SystemOnBehalfOfUser(AuthProviders.PROVIDER_PREFIX + providerId),
                null
            )
        )
    }

    override suspend fun fetchIdCard(actorAndProject: ActorAndProject, allowCached: Boolean): IdCard {
        val username = actorAndProject.actor.safeUsername()
        val project = actorAndProject.project

        return if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            val providerId = username.removePrefix(AuthProviders.PROVIDER_PREFIX)
            val productIds = products.productProviderToProductIds(providerId) ?: emptyList()
            IdCard.Provider(providerId, productIds.toIntArray())
        } else {
            mutex.withReader {
                val uid = users.indexOf(username).also { require(it != -1) }
                val adminOf = admins.indices.filter { admins[it].contains(uid) }
                val memberOf = groupMembers.indices.filter { groupMembers[it].contains(uid) }
                val activeProject = if (project != null) projects.indexOf(project) else 0

                IdCard.User(uid, memberOf.toIntArray(), adminOf.toIntArray(), activeProject)
            }
        }
    }

    override suspend fun lookupUid(uid: Int): String? {
        return mutex.withReader { users.getOrNull(uid) }
    }

    override suspend fun lookupPid(pid: Int): String? {
        return mutex.withReader { projects.getOrNull(pid) }
    }

    override suspend fun lookupGid(gid: Int): AclEntity.ProjectGroup? {
        return mutex.withReader {
            val group = groups.getOrNull(gid)
            if (group == null) {
                null
            } else {
                AclEntity.ProjectGroup(
                    lookupPid(group.first)!!,
                    group.second,
                )
            }
        }
    }

    override suspend fun lookupUidFromUsername(username: String): Int? {
        return mutex.withReader { users.indexOf(username).takeIf { it != -1 } }
    }

    override suspend fun lookupGidFromGroupId(groupId: String): Int? {
        return mutex.withReader { groups.indexOfFirst { it.second == groupId }.takeIf { it != -1 } }
    }

    override suspend fun lookupPidFromProjectId(projectId: String): Int? {
        return mutex.withReader { projects.indexOfFirst { it == projectId }.takeIf { it != -1 } }
    }

    override suspend fun fetchAllUserGroup(pid: Int): Int = 1 // TODO(Dan): Missing realistic implementation
}

class FakeResourceStoreQueries(val products: FakeProductCache) : ResourceStoreDatabaseQueries<Unit> {
    val initialResources = NonBlockingHashMapLong<ResourceDocument<Unit>>()
    val saveRequests = NonBlockingHashMapLong<Unit>()

    override suspend fun startTransaction(): Any = Unit
    override suspend fun abortTransaction(transaction: Any) {
        // OK
    }

    override suspend fun commitTransaction(transaction: Any) {
        // OK
    }

    fun addResource(resource: ResourceDocument<Unit>) {
        initialResources[resource.id] = resource
    }

    override suspend fun loadResources(transaction: Any, bucket: ResourceStoreBucket<Unit>, minimumId: Long): Long? {
        with(bucket) {
            var needsToExpand = false
            val allKeys = initialResources.keys.toList().sorted()
            for (key in allKeys) {
                val resource = initialResources[key]
                val belongsInWorkspace =
                    (pid != 0 && resource.project == pid) ||
                            (uid != 0 && resource.createdBy == uid && resource.project == 0)

                if (belongsInWorkspace && resource.id > minimumId) {
                    id[size] = resource.id
                    createdAt[size] = resource.createdAt
                    createdBy[size] = resource.createdBy
                    product[size] = resource.product
                    project[size] = resource.project
                    providerId[size] = resource.providerId
                    aclEntities[size] = resource.acl.mapNotNull { it?.entity }.toIntArray()
                    aclIsUser[size] = resource.acl.mapNotNull { it?.isUser }.toBooleanArray()
                    aclPermissions[size] = resource.acl.mapNotNull { it?.permission?.toByte() }.toByteArray()
                    data[size] = Unit
                    updates[size] = CyclicArray<ResourceDocumentUpdate>(64).also {
                        val updates = resource.update.filterNotNull()
                        for (update in updates) {
                            it.add(update)
                        }
                    }

                    size++

                    if (size >= ResourceStoreBucket.STORE_SIZE) {
                        needsToExpand = true
                        break
                    }
                }
            }
            if (needsToExpand) return id[ResourceStoreBucket.STORE_SIZE - 1]
        }
        return null
    }

    override suspend fun saveResources(
        transaction: Any,
        bucket: ResourceStoreBucket<Unit>,
        indices: IntArray,
        len: Int
    ) {
        for (i in 0 until len) {
            val arrIdx = indices[i]
            saveRequests[bucket.id[arrIdx]] = Unit

            val res = ResourceDocument<Unit>()
            bucket.loadIntoDocument(res, arrIdx)
            initialResources[res.id] = res
        }
    }

    override suspend fun loadProviderIndex(
        transaction: Any,
        type: String,
        providerId: String,
        register: suspend (uid: Int, pid: Int) -> Unit
    ) {
        val productIds = products.productProviderToProductIds(providerId) ?: emptyList()
        for (id in initialResources.keys()) {
            val resource = initialResources[id] ?: continue
            if (resource.product in productIds) {
                register(
                    if (resource.project == 0) resource.createdBy else 0,
                    resource.project
                )
            }
        }
    }

    override suspend fun loadIdIndex(
        transaction: Any,
        type: String,
        minimumId: Long,
        maximumIdExclusive: Long,
        register: (id: Long, ref: Int, isUser: Boolean) -> Unit
    ) {
        for (id in initialResources.keys()) {
            val resource = initialResources[id] ?: continue
            if (resource.id in minimumId..<maximumIdExclusive) {
                register(
                    resource.id,
                    resource.project.takeIf { it != 0 } ?: resource.createdBy,
                    resource.project == 0
                )
            }
        }
    }

    override suspend fun loadMaximumId(transaction: Any): Long {
        return initialResources.keys.maxOrNull() ?: 1L
    }
}

class ResourceStoreTest {
    data class TestContext(
        val products: FakeProductCache,
        val idCards: FakeIdCardService,
        val queries: FakeResourceStoreQueries,
    ) {
        // NOTE(Dan): This is lazy to ensure that queries can be populated before the store begins to read from it.
        val store: ResourceStore<Unit> by lazy {
            ResourceStore("jobs", queries, products, idCards, object : ResourceStore.Callbacks<Unit> {
                override suspend fun loadState(transaction: Any, count: Int, resources: LongArray): Array<Unit> {
                    return Array(count) {}
                }

                override suspend fun saveState(
                    transaction: Any,
                    store: ResourceStoreBucket<Unit>,
                    indices: IntArray,
                    length: Int
                ) {
                    // OK
                }
            })
        }
    }

    fun test(block: suspend TestContext.() -> Unit) {
        val products = FakeProductCache()
        val idCards = FakeIdCardService(products)
        val queries = FakeResourceStoreQueries(products)

        runBlocking {
            TestContext(products, idCards, queries).block()
        }
    }

    data class TestUser(
        private val ctx: TestContext,
        val uid: Int,
        val username: String,
        val actorAndProject: ActorAndProject
    ) {
        suspend fun idCard(project: String? = null) = ctx.idCards.fetchIdCard(actorAndProject.copy(project = project))
    }

    suspend fun TestContext.createUser(username: String): TestUser {
        val uid = idCards.createUser(username)
        val actorAndProject = ActorAndProject(Actor.SystemOnBehalfOfUser(username), null)
        return TestUser(this, uid, username, actorAndProject)
    }

    @Test
    fun `read empty store`() = test {
        val user = createUser("user")
        val idCard = user.idCard()
        ResourceOutputPool.withInstance<Unit, Unit> { pool ->
            run {
                val results = store.browse(idCard, pool, null, SimpleResourceIncludeFlags())
                assertEquals(0, results.count, "store should be empty")
                assertEquals(null, results.next, "store should be empty")
            }

            run {
                val results = store.browseWithSort(
                    idCard,
                    pool,
                    null,
                    SimpleResourceIncludeFlags(),
                    keyExtractor = object : DocKeyExtractor<Unit, Long> {
                        override fun extract(doc: ResourceDocument<Unit>): Long = doc.id
                    },
                    comparator = Comparator.naturalOrder(),
                    sortDirection = SortDirection.ascending,
                )
                assertEquals(0, results.count, "store should be empty even when sorting")
                assertEquals(null, results.next, "store should be empty even when sorting")
            }

            run {
                assertNull(store.retrieve(idCard, 0), "store should be empty")
                assertNull(store.retrieve(idCard, 1), "store should be empty")
            }
        }
    }

    @Test
    fun `test read from stored data`() = test {
        val projectTitle = "project"
        val product = products.insert("a", "b", "c")
        val admin = createUser("admin")
        val member = createUser("member")
        val outsider = createUser("outsider")
        val project = idCards.createProject(projectTitle)
        val group = idCards.createGroup(project, "group")
        idCards.addAdminToProject(admin.uid, project)
        idCards.addUserToGroup(member.uid, group)

        val adminOnlyResource = 100L
        val adminOnlyByMember = 102L
        val memberByAdminViaGroup = 103L
        val memberByMemberViaAcl = 104L
        val personalResource = 105L

        queries.addResource(
            ResourceDocument(
                id = adminOnlyResource,
                createdBy = admin.uid,
                project = project,
                product = product,
                data = Unit,
            )
        )

        queries.addResource(
            ResourceDocument(
                id = adminOnlyByMember,
                createdBy = member.uid,
                project = project,
                product = product,
                data = Unit,
            )
        )

        queries.addResource(
            ResourceDocument(
                id = memberByAdminViaGroup,
                createdBy = admin.uid,
                project = project,
                product = product,
                data = Unit,
            ).apply {
                acl.add(ResourceDocument.AclEntry(group, false, Permission.READ))
            }
        )

        queries.addResource(
            ResourceDocument(
                id = memberByMemberViaAcl,
                createdBy = member.uid,
                project = project,
                product = product,
                data = Unit,
                acl = arrayListOf(ResourceDocument.AclEntry(member.uid, true, Permission.READ))
            )
        )

        queries.addResource(
            ResourceDocument(
                id = personalResource,
                createdBy = member.uid,
                project = 0,
                product = product,
                data = Unit,
                acl = arrayListOf(ResourceDocument.AclEntry(member.uid, true, Permission.READ))
            )
        )

        run {
            // The member should be able to read their own personal resource
            val idCard = member.idCard(project = null)
            ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                val results = store.browse(idCard, pool, null, SimpleResourceIncludeFlags())
                assertEquals(1, results.count)
                assertNull(results.next)
                assertEquals(personalResource, pool[0].id)
            }
        }

        run {
            // Admin should be able to read all four resources owned by the project
            val idCard = admin.idCard(projectTitle)

            ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                val results = store.browse(
                    idCard,
                    pool,
                    null,
                    SimpleResourceIncludeFlags(),
                    sortDirection = SortDirection.descending,
                )
                assertEquals(4, results.count)
                assertNull(results.next)
                assertEquals(memberByMemberViaAcl, pool[0].id)
                assertTrue("${pool[0].id} should be larger than ${pool[1].id}") {
                    pool[0].id > pool[1].id
                }
            }

            ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                val results = store.browse(
                    idCard,
                    pool,
                    null,
                    SimpleResourceIncludeFlags(),
                    sortDirection = SortDirection.ascending
                )
                assertEquals(4, results.count)
                assertNull(results.next)
                assertEquals(adminOnlyResource, pool[0].id)
                assertTrue("${pool[0].id} should be smaller than ${pool[1].id}") {
                    pool[0].id < pool[1].id
                }

                for (idx in 0 until results.count) {
                    assertNotNull(store.retrieve(idCard, pool[idx].id, Permission.READ))
                    assertNotNull(store.retrieve(idCard, pool[idx].id, Permission.EDIT))
                    assertNotNull(store.retrieve(idCard, pool[idx].id, Permission.ADMIN))
                    assertNull(store.retrieve(idCard, pool[idx].id, Permission.PROVIDER))
                }
            }
        }

        run {
            // Members should only be able to read some of the resources and none when asking for EDIT/ADMIN/PROVIDER
            val idCard = member.idCard(projectTitle)

            ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                val results = store.browse(
                    idCard,
                    pool,
                    null,
                    SimpleResourceIncludeFlags(),
                    sortDirection = SortDirection.descending,
                )
                assertEquals(2, results.count)
                assertNull(results.next)
                assertEquals(memberByMemberViaAcl, pool[0].id)
                assertTrue("${pool[0].id} should be larger than ${pool[1].id}") {
                    pool[0].id > pool[1].id
                }
            }

            ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                val results = store.browse(
                    idCard,
                    pool,
                    null,
                    SimpleResourceIncludeFlags(),
                    sortDirection = SortDirection.ascending
                )
                assertEquals(2, results.count)
                assertNull(results.next)
                assertEquals(memberByAdminViaGroup, pool[0].id)
                assertTrue("${pool[0].id} should be smaller than ${pool[1].id}") {
                    pool[0].id < pool[1].id
                }

                for (idx in 0 until results.count) {
                    assertNotNull(store.retrieve(idCard, pool[idx].id, Permission.READ))
                    assertNull(store.retrieve(idCard, pool[idx].id, Permission.EDIT))
                    assertNull(store.retrieve(idCard, pool[idx].id, Permission.ADMIN))
                    assertNull(store.retrieve(idCard, pool[idx].id, Permission.PROVIDER))
                }
            }
        }

        run {
            for (p in listOf(projectTitle, null)) {
                // Outsiders should not be able to read anything
                val idCard = outsider.idCard(p)
                ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                    val results = store.browse(
                        idCard,
                        pool,
                        null,
                        SimpleResourceIncludeFlags(),
                        sortDirection = SortDirection.descending,
                    )
                    assertEquals(0, results.count)
                    assertNull(results.next)
                }

                ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                    val results = store.browse(
                        idCard,
                        pool,
                        null,
                        SimpleResourceIncludeFlags(),
                        sortDirection = SortDirection.ascending
                    )
                    assertEquals(0, results.count)
                    assertNull(results.next)
                }

                for (id in 100..120) {
                    for (perm in Permission.entries) {
                        assertNull(store.retrieve(idCard, id.toLong(), perm))
                    }
                }
            }
        }
    }

    @Test
    fun `test create and read - no stored data`() = test {
        val projectTitle = "project"
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val admin = createUser("admin")
        val project = idCards.createProject(projectTitle)
        idCards.addAdminToProject(admin.uid, project)

        val idCard = admin.idCard(projectTitle)
        val ids = ArrayList<Long>()
        val creationCount = 10_000
        repeat(creationCount) {
            ids.add(
                store.create(
                    idCard,
                    ref,
                    Unit,
                    null,
                )
            )
        }

        assertEquals(creationCount, ids.distinct().size)

        for (id in ids) {
            assertNotNull(store.retrieve(idCard, id, Permission.READ))
            assertNotNull(store.retrieve(idCard, id, Permission.EDIT))
            assertNotNull(store.retrieve(idCard, id, Permission.ADMIN))
            assertNull(store.retrieve(idCard, id, Permission.PROVIDER))
        }

        testBrowse(idCard, ids)
    }

    enum class SortType {
        CUSTOM,
        NONE,
        CREATED_BY
    }

    private suspend fun TestContext.testBrowse(
        idCard: IdCard,
        ids: List<Long>,
        doCustomSorting: Boolean = true,
    ) {
        val dirs = listOf(SortDirection.ascending, SortDirection.descending)
        val shouldSort = SortType.entries
        for (sort in shouldSort) {
            if (sort != SortType.NONE && !doCustomSorting) continue
            for (dir in dirs) {
                val collectedIds = ArrayList<Long>()
                var next: String? = null
                var shouldBreak = false
                val itemsPerPage = ArrayList<Int>()
                val tokens = ArrayList<String>()
                val pages = ArrayList<List<Long>>()
                while (!shouldBreak) {
                    ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                        val res =
                            when {
                                sort == SortType.CREATED_BY -> {
                                    store.browse(
                                        idCard,
                                        pool,
                                        next,
                                        SimpleResourceIncludeFlags(),
                                        sortDirection = dir,
                                        sortedBy = "createdBy",
                                    )
                                }

                                sort == SortType.CUSTOM -> {
                                    store.browseWithSort(
                                        idCard,
                                        pool,
                                        next,
                                        SimpleResourceIncludeFlags(),
                                        keyExtractor = object : DocKeyExtractor<Unit, Long> {
                                            override fun extract(doc: ResourceDocument<Unit>): Long = doc.id
                                        },
                                        comparator = Comparator.naturalOrder(),
                                        sortDirection = dir,
                                    )
                                }

                                else -> {
                                    store.browse(
                                        idCard,
                                        pool,
                                        next,
                                        SimpleResourceIncludeFlags(),
                                        sortDirection = dir,
                                    )
                                }
                            }

                        itemsPerPage.add(res.count)


                        val newPage = ArrayList<Long>()
                        for (i in 0 until res.count) {
                            collectedIds.add(pool[i].id)
                            newPage.add(pool[i].id)
                        }
                        pages.add(newPage)

                        next = res.next
                        if (next == null) shouldBreak = true
                        else tokens.add(next!!)
                    }
                }

                if (ids.size != collectedIds.size || ids.sorted() != collectedIds.sorted()) {
                    val allIds = ids.toSet()
                    val duplicateCollected = HashMap<Long, Int>()
                    val allCollected = HashSet<Long>()
                    println(itemsPerPage)
                    println(tokens)
                    println("------")
                    println("Pages:")
                    for (page in pages) {
                        println("${page.first()} - ${page.takeLast(3)} (${page.size})")
                    }
                    println("------")
                    for ((index, collected) in collectedIds.withIndex()) {
                        if (collected in allCollected) {
                            val previous = duplicateCollected[collected]
                            println("Duplicate: $collected at index $index was previously at $previous")
                        }
                        duplicateCollected[collected] = index
                        allCollected.add(collected)
                    }

                    data class Info(val id: Long, val uid: Int?, val pid: Int?)

                    val missingInOutput = ArrayList<Info>()
                    val unexpectedInOutput = ArrayList<Info>()
                    for (id in ids) {
                        if (id !in allCollected) {
                            val retrieved = store.retrieve(IdCard.System, id)
                            missingInOutput.add(Info(id, retrieved?.createdBy, retrieved?.project))
                        }
                    }

                    for (id in collectedIds) {
                        if (id !in allIds) {
                            val retrieved = store.retrieve(IdCard.System, id)
                            missingInOutput.add(Info(id, retrieved?.createdBy, retrieved?.project))
                        }
                    }

                    for (missing in missingInOutput.take(50)) {
                        println("Missing: $missing")
                    }
                    for (unexpected in unexpectedInOutput.take(50)) {
                        println("Unexpected: $unexpected")
                    }
                    println(
                        "($sort $dir) Number of missing: ${missingInOutput.size}. " +
                                "Number of unexpected: ${unexpectedInOutput.size}. " +
                                "Expected items: ${allIds.size} (${ids.size}). " +
                                "Items collected: ${allCollected.size} (${collectedIds.size}). "
                    )

                    assertTrue(false, "Did not find the expected elements. See stdout for details.")
                }
            }
        }
    }

    @Test
    fun `test many workspaces concurrent`() = test {
        val workspaceCount = 100
        val resourcePerWorkspace = 2_000
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        coroutineScope {
            val allIds = (0..<workspaceCount).chunked(Runtime.getRuntime().availableProcessors()).flatMap { chunk ->
                chunk.map { i ->
                    async(Dispatchers.IO) {
                        val user = createUser("user$i")
                        val idCard = user.idCard()
                        val ids = ArrayList<Long>()
                        repeat(resourcePerWorkspace) { j ->
                            ids.add(
                                store.create(
                                    idCard,
                                    ref,
                                    Unit,
                                    null,
                                )
                            )
                        }

                        testBrowse(idCard, ids, false)

                        ids
                    }
                }.awaitAll().flatten()
            }

            val providerCard = idCards.fetchProvider(ref.provider)
            testBrowse(providerCard, allIds, false)
        }
    }

    @Test
    fun `test created acl access`() = test {
        val projectTitle = "project"
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val admin = createUser("admin")
        val member = createUser("member")
        val project = idCards.createProject(projectTitle)
        val group = idCards.createGroup(project, "group")
        idCards.addAdminToProject(admin.uid, project)
        idCards.addUserToGroup(member.uid, group)
        idCards.addUserToGroup(admin.uid, group)

        val id = store.create(
            admin.idCard(projectTitle),
            ref,
            Unit,
            null,
        )

        assertEquals(null, store.retrieve(member.idCard(projectTitle), id))

        // Should fail because member is not allowed to change ACL
        store.updateAcl(
            member.idCard(projectTitle),
            id,
            emptyList(),
            listOf(NumericAclEntry(gid = group, permission = Permission.READ))
        )

        assertNotNull(store.retrieve(admin.idCard(projectTitle), id))
        assertNull(store.retrieve(member.idCard(projectTitle), id))
        assertNull(store.retrieve(member.idCard(projectTitle), id, Permission.EDIT))

        // Should succeed because the admin is allowed to do it
        store.updateAcl(
            admin.idCard(projectTitle),
            id,
            emptyList(),
            listOf(NumericAclEntry(gid = group, permission = Permission.READ))
        )
        assertNotNull(store.retrieve(admin.idCard(projectTitle), id))
        assertNotNull(store.retrieve(member.idCard(projectTitle), id))
        assertNull(store.retrieve(member.idCard(projectTitle), id, Permission.EDIT))

        // Removing the entry should make it inaccessible to the member
        store.updateAcl(
            admin.idCard(projectTitle),
            id,
            listOf(NumericAclEntry(gid = group)),
            emptyList()
        )

        assertNotNull(store.retrieve(admin.idCard(projectTitle), id))
        assertNull(store.retrieve(member.idCard(projectTitle), id, Permission.READ))
        assertNull(store.retrieve(member.idCard(projectTitle), id, Permission.EDIT))

        // Deletions and additions should be allowed in the same request, meaning that the member should have read
        // and edit
        store.updateAcl(
            admin.idCard(projectTitle),
            id,
            listOf(NumericAclEntry(gid = group)),
            listOf(
                NumericAclEntry(gid = group, permission = Permission.READ),
                NumericAclEntry(gid = group, permission = Permission.EDIT),
            )
        )

        assertNotNull(store.retrieve(admin.idCard(projectTitle), id))
        assertNotNull(store.retrieve(member.idCard(projectTitle), id, Permission.READ))
        assertNotNull(store.retrieve(member.idCard(projectTitle), id, Permission.EDIT))

        // Verify that the ACL looks correct from admin point of view
        val doc = store.retrieve(admin.idCard(projectTitle), id)!!
        assertEquals(2, doc.acl.count { it != null })
        assertTrue(doc.acl.all { it == null || (!it.isUser && it.entity == group) })
        assertTrue(doc.acl.any { it != null && it.permission == Permission.READ })
        assertTrue(doc.acl.any { it != null && it.permission == Permission.EDIT })
    }

    @Test
    fun `test provider pagination`() = test {
        val projectTitle = "project"
        val projectTitle2 = "project2"
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val admin = createUser("admin")
        val admin2 = createUser("admin2")
        val project = idCards.createProject(projectTitle)
        val project2 = idCards.createProject(projectTitle2)
        idCards.addAdminToProject(admin.uid, project)
        idCards.addAdminToProject(admin2.uid, project2)

        // NOTE(Dan): Randomly selected seed by mashing buttons on my keyboard
        val random = Random(5348192)

        data class WorkspaceCreation(
            val card: IdCard,

            // NOTE(Dan): This will guarantee that we need multiple pages _and_ multiple buckets per workspace.
            var remaining: Int = 2000 + random.nextInt(0, 452),

            val myIds: ArrayList<Long> = ArrayList(),
        )

        val workspaces = listOf(
            WorkspaceCreation(admin.idCard()),
            WorkspaceCreation(admin2.idCard(projectTitle2)),
            WorkspaceCreation(admin2.idCard()),
            WorkspaceCreation(admin.idCard(projectTitle)),
        )

        val allIds = ArrayList<Long>()
        while (true) {
            val workspace = workspaces.random(random)
            if (workspace.remaining <= 0) {
                if (workspaces.all { it.remaining <= 0 }) break
                else continue
            }
            val resourcesToCreate = random.nextInt(1, min(100, workspace.remaining + 1))
            repeat(resourcesToCreate) {
                val id = store.create(workspace.card, ref, Unit, null)
                workspace.myIds.add(id)
                allIds.add(id)
            }
            workspace.remaining -= resourcesToCreate
        }

        for (workspace in workspaces) {
            testBrowse(workspace.card, workspace.myIds, false)
        }

        val providerCard = idCards.fetchProvider(ref.provider)
        testBrowse(providerCard, allIds, false)
    }

    @Test
    fun `test large acls`() = test {
        val groupCount = 1_000

        val projectTitle = "project"
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        val admin = createUser("admin")
        val project = idCards.createProject(projectTitle)
        idCards.addAdminToProject(admin.uid, project)

        val members = (0..<groupCount).map { createUser("member$it") }
        val groups = (0..<groupCount).map { idCards.createGroup(project, "group$it") }
        for (i in 0..<groupCount) {
            idCards.addUserToGroup(admin.uid, groups[i])
            idCards.addUserToGroup(members[i].uid, groups[i])
        }

        // Start with a test where we have many different ACLs
        val ids = ArrayList<Long>()
        for (i in 0..<groupCount) {
            val id = store.create(admin.idCard(projectTitle), ref, Unit, null)
            ids.add(id)
            store.updateAcl(
                admin.idCard(projectTitle),
                id,
                emptyList(),
                listOf(NumericAclEntry(gid = groups[i], permission = Permission.READ))
            )
        }

        for (i in 0..<groupCount) {
            assertNotNull(store.retrieve(members[i].idCard(), ids[i]))
            testBrowse(members[i].idCard(projectTitle), listOf(ids[i]), false)
        }

        testBrowse(admin.idCard(projectTitle), ids, false)

        // Then another test where we have one very big ACL
        val bigAclId = store.create(admin.idCard(projectTitle), ref, Unit, null)
        store.updateAcl(
            admin.idCard(projectTitle),
            bigAclId,
            emptyList(),
            groups.map { gid ->
                NumericAclEntry(gid = gid, permission = Permission.READ)
            }
        )

        for (i in 0..<groupCount) {
            assertNotNull(store.retrieve(members[i].idCard(), bigAclId, Permission.READ))
            assertNull(store.retrieve(members[i].idCard(), bigAclId, Permission.EDIT))
        }
    }

    @Test
    fun `test mixed acl`() = test {
        val projectTitle = "project"
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        val admin = createUser("admin")
        val member = createUser("member")
        val user = createUser("user")
        val project = idCards.createProject(projectTitle)
        val group = idCards.createGroup(project, "group")
        idCards.addAdminToProject(admin.uid, project)
        idCards.addUserToGroup(member.uid, group)

        val id = store.create(admin.idCard(), ref, Unit, null)
        assertNotNull(store.retrieve(admin.idCard(), id))
        assertNull(store.retrieve(member.idCard(), id))
        assertNull(store.retrieve(user.idCard(), id))

        store.updateAcl(
            admin.idCard(),
            id,
            emptyList(),
            listOf(NumericAclEntry(gid = group, permission = Permission.READ))
        )
        assertNotNull(store.retrieve(admin.idCard(), id))
        assertNotNull(store.retrieve(member.idCard(), id))
        assertNull(store.retrieve(user.idCard(), id))

        store.updateAcl(
            admin.idCard(),
            id,
            emptyList(),
            listOf(NumericAclEntry(uid = user.uid, permission = Permission.READ))
        )
        assertNotNull(store.retrieve(admin.idCard(), id))
        assertNotNull(store.retrieve(member.idCard(), id))
        assertNotNull(store.retrieve(user.idCard(), id))

        store.updateAcl(admin.idCard(), id, listOf(NumericAclEntry(uid = user.uid)), emptyList())
        assertNotNull(store.retrieve(admin.idCard(), id))
        assertNotNull(store.retrieve(member.idCard(), id))
        assertNull(store.retrieve(user.idCard(), id))

        store.updateAcl(admin.idCard(), id, listOf(NumericAclEntry(gid = group)), emptyList())
        assertNotNull(store.retrieve(admin.idCard(), id))
        assertNull(store.retrieve(member.idCard(), id))
        assertNull(store.retrieve(user.idCard(), id))
    }

    @Test
    fun `test bad acl updates`() = test {
        val user = createUser("user")
        val user2 = createUser("user2")
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        val id = store.create(user.idCard(), ref, Unit, null)
        assertFails {
            store.updateAcl(
                user.idCard(),
                id,
                emptyList(),
                listOf(NumericAclEntry(uid = user2.uid, permission = Permission.ADMIN))
            )
        }

        assertFails {
            store.updateAcl(
                user.idCard(),
                id,
                emptyList(),
                listOf(NumericAclEntry(uid = user2.uid, permission = Permission.PROVIDER))
            )
        }
    }

    @Test
    fun `test filtering`() = test {
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val user = createUser("user")
        val providerCard = idCards.fetchProvider(ref.provider)

        suspend fun browseWithFlags(
            pool: Array<ResourceDocument<Unit>>,
            flags: SimpleResourceIncludeFlags
        ): Int {
            return store.browse(user.idCard(), pool, null, flags).count
        }

        val ids = ArrayList<Long>()
        val numberOfResources = 30
        repeat(numberOfResources) {
            ids.add(store.create(user.idCard(), ref, Unit, null))
        }

        ResourceOutputPool.withInstance<Unit, Unit> { pool ->

            run {
                // filterProviderIds
                for (id in ids) {
                    store.updateProviderId(providerCard, id, "$id")
                }

                val filterProviderIds = ids.take(10).joinToString(",") { "$it" }
                assertEquals(
                    10,
                    browseWithFlags(pool, SimpleResourceIncludeFlags(filterProviderIds = filterProviderIds))
                )
                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(filterProviderIds = "")))
            }

            run {
                // filterCreatedBefore and filterCreatedAfter
                assertEquals(1, store.retrieveBulk(providerCard, longArrayOf(ids[0]), pool, Permission.PROVIDER))
                assertEquals(1, store.modify(providerCard, pool, longArrayOf(ids[0]), Permission.PROVIDER) { idx, doc ->
                    createdAt[idx] = 1000L
                })

                assertEquals(1, browseWithFlags(pool, SimpleResourceIncludeFlags(filterCreatedBefore = 10_000L)))

                val theFuture = System.currentTimeMillis() * 2
                store.modify(providerCard, pool, longArrayOf(ids[0]), Permission.PROVIDER) { idx, doc ->
                    createdAt[idx] = theFuture
                }

                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(filterCreatedBefore = 10_000L)))
                assertEquals(1, browseWithFlags(pool, SimpleResourceIncludeFlags(filterCreatedAfter = theFuture - 1L)))

                store.modify(providerCard, pool, longArrayOf(ids[0]), Permission.PROVIDER) { idx, doc ->
                    createdAt[idx] = 1000L
                }

                store.modify(providerCard, pool, longArrayOf(ids[1]), Permission.PROVIDER) { idx, doc ->
                    createdAt[idx] = 5000L
                }
                assertEquals(
                    1,
                    browseWithFlags(
                        pool,
                        SimpleResourceIncludeFlags(
                            filterCreatedAfter = 0L,
                            filterCreatedBefore = 4999L,
                        )
                    )
                )

                assertEquals(
                    2,
                    browseWithFlags(
                        pool,
                        SimpleResourceIncludeFlags(
                            filterCreatedAfter = 0L,
                            filterCreatedBefore = 10_000L,
                        )
                    )
                )

                assertEquals(
                    1,
                    browseWithFlags(
                        pool,
                        SimpleResourceIncludeFlags(
                            filterCreatedAfter = 1001L,
                            filterCreatedBefore = 10_000L,
                        )
                    )
                )
            }

            run {
                // filterProductId
                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(filterProductId = "bad")))
                assertEquals(
                    numberOfResources,
                    browseWithFlags(pool, SimpleResourceIncludeFlags(filterProductId = ref.id))
                )
            }

            run {
                // filterProductCategory
                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(filterProductCategory = "bad")))
                assertEquals(
                    numberOfResources,
                    browseWithFlags(pool, SimpleResourceIncludeFlags(filterProductCategory = ref.category))
                )
            }

            run {
                // filterProvider
                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(filterProvider = "bad")))
                assertEquals(
                    numberOfResources,
                    browseWithFlags(pool, SimpleResourceIncludeFlags(filterProvider = ref.provider))
                )
            }

            run {
                // hideProductId
                assertEquals(
                    numberOfResources,
                    browseWithFlags(pool, SimpleResourceIncludeFlags(hideProductId = "bad"))
                )
                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(hideProductId = ref.id)))
            }

            run {
                // hideProductCategory
                assertEquals(
                    numberOfResources,
                    browseWithFlags(pool, SimpleResourceIncludeFlags(hideProductCategory = "bad"))
                )
                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(hideProductCategory = ref.category)))
            }

            run {
                // hideProvider
                assertEquals(numberOfResources, browseWithFlags(pool, SimpleResourceIncludeFlags(hideProvider = "bad")))
                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(hideProvider = ref.provider)))
            }

            run {
                // filterIds
                val filterIds = (5..15).map { ids[it] }
                assertEquals(
                    filterIds.size,
                    browseWithFlags(
                        pool,
                        SimpleResourceIncludeFlags(
                            filterIds = filterIds.joinToString(",") { it.toString() }
                        )
                    )
                )

                assertEquals(0, browseWithFlags(pool, SimpleResourceIncludeFlags(filterIds = "")))
            }
        }
    }

    @Test
    fun `test register call`() = test {
        val product = products.insert("a", "b", "c")
        val product2 = products.insert("a", "b", "d")
        val ref = products.productIdToReference(product)!!
        val ref2 = products.productIdToReference(product2)!!
        val user = createUser("user")
        val providerCard = idCards.fetchProvider(ref.provider)

        val output = ResourceDocument<Unit>()
        val id = store.register(providerCard, ref, user.uid, 0, Unit, null, output = output)
        assertEquals(id, output.id)
        assertEquals(user.uid, output.createdBy)
        assertEquals(Unit, output.data)
        assertEquals(0, output.acl.size)
        assertTrue(output.update.filterNotNull().isEmpty())
        assertEquals(0, output.project)
        assertNull(output.providerId)
        val now = System.currentTimeMillis()
        assertTrue(now - output.createdAt < 5000L)

        assertTrue(runCatching { store.register(providerCard, ref2, user.uid, 0, Unit, null, output = output) }.isFailure)
        assertTrue(runCatching {
            store.register(
                providerCard,
                ProductReference("dd", "dd", "dd"),
                user.uid,
                0,
                Unit,
                null,
                output = output
            )
        }.isFailure)
        assertTrue(runCatching { store.register(user.idCard(), ref, user.uid, 0, Unit, null, output = output) }.isFailure)
    }

    @Test
    fun `test update`() = test {
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val user = createUser("user")
        val providerCard = idCards.fetchProvider(ref.provider)

        run {
            // Created resource
            val output = ResourceDocument<Unit>()
            val id = store.create(user.idCard(), ref, Unit, output)
            assertEquals(id, output.id)

            val update = "Testing"
            store.addUpdate(providerCard, id, listOf(ResourceDocumentUpdate(update)))
            assertEquals(update, store.retrieve(user.idCard(), id)!!.update.filterNotNull().first().update)
        }

        run {
            // Registered resource
            val output = ResourceDocument<Unit>()
            val id = store.register(providerCard, ref, user.uid, 0, Unit, "providerId", output = output)
            assertEquals(id, output.id)
            assertEquals("providerId", output.providerId)

            val update = "Testing"
            store.addUpdate(providerCard, id, listOf(ResourceDocumentUpdate(update)))
            assertEquals(update, store.retrieve(user.idCard(), id)!!.update.filterNotNull().first().update)
        }

        run {
            // createViaProvider resource
            val output = ResourceDocument<Unit>()
            val id = store.createViaProvider(user.idCard(), ref, Unit, output) { "providerId" }
            assertEquals(id, output.id)
            assertEquals("providerId", output.providerId)

            val update = "Testing"
            store.addUpdate(providerCard, id, listOf(ResourceDocumentUpdate(update)))
            assertEquals(update, store.retrieve(user.idCard(), id)!!.update.filterNotNull().first().update)
        }

        run {
            // Multiple updates in single and cyclic behavior
            val counts = listOf(1, 10, 64, 100, 1, 1)
            val repeats = listOf(1, 1, 1, 1, 64, 100)

            val expectedMaxUpdates = 64 // This might need to be updated later

            for ((count, repeatCount) in counts.zip(repeats)) {
                val allUpdateMessages = ArrayList<String>()
                val id = store.create(user.idCard(), ref, Unit, null)
                var existingCount = 0
                repeat(repeatCount) {
                    val updateMessages = (0 until count).map { "$it" }
                    allUpdateMessages.addAll(updateMessages)
                    val updates = updateMessages.map { ResourceDocumentUpdate(it) }
                    store.addUpdate(providerCard, id, updates)
                    existingCount += count

                    val retrieved = store.retrieve(user.idCard(), id)!!
                    assertEquals(expectedMaxUpdates, retrieved.update.size)
                    val retrievedUpdates = retrieved.update.filterNotNull().mapNotNull { it.update }
                    val expectedUpdates = allUpdateMessages.takeLast(min(expectedMaxUpdates, existingCount))

                    assertEquals(expectedUpdates, retrievedUpdates)
                }
            }
        }
    }

    @Test
    fun `test update acl works correctly`() = test {
        val projectTitle = "project"
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        val admin = createUser("admin")
        val member = createUser("member")
        val user = createUser("user")
        val providerCard = idCards.fetchProvider(ref.provider)
        val project = idCards.createProject(projectTitle)
        val group = idCards.createGroup(project, "group")
        idCards.addAdminToProject(admin.uid, project)
        idCards.addUserToGroup(member.uid, group)

        val id = store.create(admin.idCard(projectTitle), ref, Unit, null)

        suspend fun updateAndConfirmAcl(performedBy: IdCard, shouldFail: Boolean) {
            run {
                val retrieved = store.retrieve(admin.idCard(projectTitle), id, Permission.READ)
                assertNotNull(retrieved)
                val acl = retrieved.acl.filterNotNull()
                assertEquals(0, acl.size)
            }

            store.updateAcl(
                performedBy,
                id,
                emptyList(),
                listOf(NumericAclEntry(uid = user.uid, permission = Permission.READ))
            )

            val retrieved = store.retrieve(admin.idCard(projectTitle), id, Permission.READ)
            assertNotNull(retrieved)
            val acl = retrieved.acl.filterNotNull()

            if (shouldFail) {
                assertEquals(0, acl.size)
            } else {
                assertEquals(1, acl.size)
                assertTrue(acl.single().isUser)
                assertEquals(user.uid, acl.single().entity)
                assertEquals(Permission.READ, acl.single().permission)
            }

            store.updateAcl(
                admin.idCard(projectTitle),
                id,
                listOf(NumericAclEntry(uid = user.uid)),
                emptyList(),
            )
        }

        updateAndConfirmAcl(admin.idCard(projectTitle), shouldFail = false)
        updateAndConfirmAcl(member.idCard(projectTitle), shouldFail = true)
        updateAndConfirmAcl(user.idCard(projectTitle), shouldFail = true)
        updateAndConfirmAcl(providerCard, shouldFail = false)
    }

    @Test
    fun `test that providers cannot create resources`() = test {
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        val providerCard = idCards.fetchProvider(ref.provider)
        assertFails { store.create(providerCard, ref, Unit, null) }
    }

    @Test
    fun `test that create does not allow invalid products`() = test {
        val badProduct = ProductReference("bad", "bad", "bad")
        val user = createUser("user")
        assertFails { store.create(user.idCard(), badProduct, Unit, null) }
    }

    @Test
    fun `test that register does not allow invalid products`() = test {
        val badProduct = ProductReference("bad", "bad", "bad")
        val otherProduct = products.insert("a", "b", "c")
        val otherProductRef = products.productIdToReference(otherProduct)!!
        val providerCard = idCards.fetchProvider("provider")
        val user = createUser("user")

        assertFails { store.register(providerCard, badProduct, user.uid, 0, Unit, null, output = null) }
        assertFails { store.register(providerCard, otherProductRef, user.uid, 0, Unit, null, output = null) }
    }

    @Test
    fun `test deletion of resources`() = test {
        val projectTitle = "project"
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        val admin = createUser("admin")
        val member = createUser("member")
        val user = createUser("user")
        val providerCard = idCards.fetchProvider(ref.provider)
        val project = idCards.createProject(projectTitle)
        val group = idCards.createGroup(project, "group")
        idCards.addAdminToProject(admin.uid, project)
        idCards.addUserToGroup(member.uid, group)

        suspend fun testDeletion(
            deletedBy: IdCard,
            permissionsToAdd: List<Permission>,
            shouldSucceed: Boolean
        ) {
            val createdBy = admin.idCard(projectTitle)
            testBrowse(createdBy, emptyList())
            testBrowse(providerCard, emptyList())

            val id = store.create(createdBy, ref, Unit, null)
            if (permissionsToAdd.isNotEmpty()) {
                store.updateAcl(
                    createdBy,
                    id,
                    emptyList(),
                    permissionsToAdd.map {
                        NumericAclEntry(gid = group, permission = it)
                    }
                )
            }

            testBrowse(providerCard, listOf(id), doCustomSorting = false)
            testBrowse(createdBy, listOf(id), doCustomSorting = false)
            if (Permission.READ in permissionsToAdd) assertNotNull(store.retrieve(deletedBy, id))
            assertNotNull(store.retrieve(createdBy, id))

            val deletedCount = store.delete(deletedBy, longArrayOf(id))
            if (shouldSucceed) assertEquals(1, deletedCount)
            else assertEquals(0, deletedCount)

            if (shouldSucceed) {
                testBrowse(createdBy, emptyList())
                testBrowse(providerCard, emptyList())
                assertNull(store.retrieve(createdBy, id))
            } else {
                testBrowse(createdBy, listOf(id))
                testBrowse(providerCard, listOf(id))
                assertNotNull(store.retrieve(createdBy, id))

                assertEquals(1, store.delete(createdBy, longArrayOf(id)))
                assertNull(store.retrieve(createdBy, id))
            }
        }

        testDeletion(admin.idCard(projectTitle), emptyList(), true)
        testDeletion(admin.idCard(projectTitle), listOf(Permission.READ), true)
        testDeletion(providerCard, emptyList(), true)
        testDeletion(providerCard, listOf(Permission.READ), true)
        testDeletion(member.idCard(), listOf(Permission.READ), false)
        testDeletion(member.idCard(), listOf(Permission.READ, Permission.EDIT), true)
        testDeletion(member.idCard(), listOf(Permission.EDIT), true)
        testDeletion(user.idCard(), emptyList(), false)
        testDeletion(user.idCard(), listOf(Permission.EDIT), false)
    }

    @Test
    fun `test that the correct data is saved`() = test {
        val user = createUser("user")
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val providerCard = idCards.fetchProvider(ref.provider)

        suspend fun withSaveRequests(block: suspend (List<Long>) -> Unit) {
            store.synchronizeNow()
            val saveRequests = queries.saveRequests.keys().toList()
            queries.saveRequests.clear()
            block(saveRequests)
        }

        val initialIds = ArrayList<Long>()
        repeat(100) {
            initialIds.add(it + 1L)
            queries.addResource(
                ResourceDocument(
                    id = it + 1L,
                    createdBy = user.uid,
                    product = product,
                    data = Unit,
                )
            )
        }

        testBrowse(user.idCard(), initialIds)

        withSaveRequests { reqs ->
            assertEquals(0, reqs.size)
        }

        // Test that modification of a single element works
        ResourceOutputPool.withInstance<Unit, Unit> { pool ->
            val id = initialIds[5]
            store.modify(user.idCard(), pool, longArrayOf(id), Permission.EDIT) { arrIdx, doc ->
                createdAt[arrIdx] = 1111L
            }

            withSaveRequests { reqs ->
                assertEquals(1, reqs.size)
                assertEquals(id, reqs.single())
            }

            withSaveRequests { reqs ->
                assertEquals(0, reqs.size)
            }
        }

        // Test that bulk modification of a works
        ResourceOutputPool.withInstance<Unit, Unit> { pool ->
            store.modify(user.idCard(), pool, initialIds.toLongArray(), Permission.EDIT) { arrIdx, doc ->
                createdAt[arrIdx] = 1111L
            }

            withSaveRequests { reqs ->
                assertEquals(initialIds.size, reqs.size)
                assertEquals(initialIds.toSet(), reqs.toSet())
            }

            withSaveRequests { reqs ->
                assertEquals(0, reqs.size)
            }
        }

        // Test that create triggers a save
        run {
            val id = store.create(user.idCard(), ref, Unit, null)
            withSaveRequests { reqs ->
                assertEquals(1, reqs.size)
                assertEquals(id, reqs.single())
            }
        }

        // Test that many creates triggers a save
        run {
            val ids = (0 until 2000).map { store.create(user.idCard(), ref, Unit, null) }
            withSaveRequests { reqs ->
                assertEquals(ids.size, reqs.size)
                assertEquals(ids.toSet(), reqs.toSet())
            }
        }

        // Test that a register triggers a save
        run {
            val ids = (0 until 50).map { store.create(user.idCard(), ref, Unit, null) }
            withSaveRequests { reqs ->
                assertEquals(ids.size, reqs.size)
                assertEquals(ids.toSet(), reqs.toSet())
            }
        }

        // Test that deletes are part of the save
        run {
            val id = store.create(user.idCard(), ref, Unit, null)
            assertEquals(1, store.delete(user.idCard(), longArrayOf(id)))
            withSaveRequests { reqs ->
                assertEquals(1, reqs.size)
                assertEquals(id, reqs.single())
            }
        }

        withSaveRequests { reqs ->
            assertEquals(0, reqs.size)
        }
    }

    @Test
    fun `test crashing during createViaProvider`() = test {
        val user = createUser("user")
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val providerCard = idCards.fetchProvider(ref.provider)

        testBrowse(user.idCard(), emptyList())
        testBrowse(providerCard, emptyList())

        assertFails {
            store.createViaProvider(user.idCard(), ref, Unit) {
                error("Crashing!")
            }
        }

        testBrowse(user.idCard(), emptyList())
        testBrowse(providerCard, emptyList())
    }

    @Test
    fun `test complicated delete`() = test {
        val user = createUser("user")
        val user2 = createUser("user2")
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!
        val providerCard = idCards.fetchProvider(ref.provider)

        val id = store.create(user.idCard(), ref, Unit, null)
        store.updateAcl(
            user.idCard(),
            id,
            emptyList(),
            listOf(
                NumericAclEntry(uid = user2.uid, permission = Permission.READ),
                NumericAclEntry(uid = user2.uid, permission = Permission.EDIT),
            )
        )
        repeat(5) {
            store.addUpdate(providerCard, id, listOf(ResourceDocumentUpdate("Test $it")))
        }

        assertNotNull(store.retrieve(user.idCard(), id))

        store.delete(user.idCard(), longArrayOf(id))

        assertNull(store.retrieve(user.idCard(), id))
    }

    @Test
    fun `test evictions`() = test {
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        coroutineScope {
            val users = (0 until Runtime.getRuntime().availableProcessors()).map { createUser("user$it") }
            for (user in users) testBrowse(user.idCard(), emptyList())
            val didEvict = AtomicBoolean(false)

            val evictJob = launch(Dispatchers.IO) {
                delay(2000)
                for (user in users) store.evict(user.uid, 0)
                didEvict.set(true)
            }

            val jobs = users.map { user ->
                async(Dispatchers.IO) {
                    val myIds = ArrayList<Long>()
                    try {
                        var deadline: Long? = null
                        while (true) {
                            val id = store.create(user.idCard(), ref, Unit, null)

                            if (myIds.isNotEmpty() && deadline == null && didEvict.get()) {
                                deadline = System.currentTimeMillis() + 2000
                            }

                            myIds.add(id)

                            if (deadline != null && System.currentTimeMillis() > deadline) break
                        }
                    } catch (ex: Throwable) {
                        ex.printStackTrace()
                    }
                    myIds
                }
            }

            evictJob.join()
            val allIds = jobs.awaitAll()
            for ((user, ids) in users.zip(allIds)) {
                testBrowse(user.idCard(), ids, doCustomSorting = false)
            }

            val createdBefore = queries.saveRequests.keys.size
            assertTrue(createdBefore > 0)
            store.synchronizeNow()
            val totalCount = allIds.fold(0) { a, b -> a + b.size }
            assertEquals(totalCount, queries.saveRequests.keys.size)
            assertNotEquals(totalCount, createdBefore)
        }
    }
}
