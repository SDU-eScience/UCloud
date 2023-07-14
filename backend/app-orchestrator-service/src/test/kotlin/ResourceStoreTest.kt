package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.SortDirection
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.accounting.util.ResourceDocumentUpdate
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.SimpleResourceIncludeFlags
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.elasticsearch.action.support.ActionFilter.Simple
import kotlin.test.*

fun Product.toReference(): ProductReference {
    return ProductReference(id, category.name, category.provider)
}

class FakeProductCache : IProductCache {
    private val mutex = ReadWriterMutex()
    private val products = ArrayList<Product>()

    suspend fun insert(product: Product): Int {
        return mutex.withWriter {
            products.add(product)
            products.size - 1
        }
    }

    suspend fun insert(name: String, category: String, provider: String): Int {
        return insert(Product.Compute(name, 1L, ProductCategoryId(category, provider), description = "Test"))
    }

    override suspend fun referenceToProductId(ref: ProductReference): Int? {
        return mutex.withReader {
            products.indexOfFirst { it.toReference() == ref }.takeIf { it != -1 }
        }
    }

    override suspend fun productIdToReference(id: Int): ProductReference? {
        return mutex.withReader { products.getOrNull(id)?.toReference() }
    }

    override suspend fun productIdToProduct(id: Int): Product? {
        return mutex.withReader { products.getOrNull(id) }
    }

    override suspend fun productNameToProductIds(name: String): List<Int>? {
        return mutex.withReader {
            val result = ArrayList<Int>()
            for (i in products.indices) {
                if (products[i].name == name) result.add(i)
            }
            result.takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun productCategoryToProductIds(category: String): List<Int>? {
        return mutex.withReader {
            val result = ArrayList<Int>()
            for (i in products.indices) {
                if (products[i].category.name == category) result.add(i)
            }
            result.takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun productProviderToProductIds(provider: String): List<Int>? {
        return mutex.withReader {
            val result = ArrayList<Int>()
            for (i in products.indices) {
                if (products[i].category.provider == provider) result.add(i)
            }
            result.takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun products(): List<Product> {
        return mutex.withReader { ArrayList(products) }
    }
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

    override suspend fun fetchIdCard(actorAndProject: ActorAndProject): IdCard {
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
}

class FakeResourceStoreQueries(val products: FakeProductCache) : ResourceStoreDatabaseQueries<Unit> {
    val initialResources = ArrayList<ResourceDocument<Unit>>()
    val mutex = Mutex()

    override suspend fun loadResources(bucket: ResourceStoreBucket<Unit>, minimumId: Long) {
        with(bucket) {
            mutex.withLock {
                for (resource in initialResources) {
                    val belongsInWorkspace =
                        (pid != 0 && resource.project == pid) ||
                            (uid != 0 && resource.createdBy == uid && resource.project == 0)

                    if (belongsInWorkspace && resource.id >= minimumId) {
                        id[size] = resource.id
                        createdAt[size] = resource.createdAt
                        createdBy[size] = resource.createdBy
                        product[size] = resource.product
                        project[size] = resource.project
                        providerId[size] = resource.providerId
                        aclEntities[size] = resource.acl.mapNotNull { it?.entity }.toIntArray()
                        aclIsUser[size] = resource.acl.mapNotNull { it?.isUser }.toBooleanArray()
                        aclPermissions[size] = resource.acl.mapNotNull { it?.permission?.toByte() }.toByteArray()
                        _data[size] = Unit
                        updates[size] = CyclicArray<ResourceDocumentUpdate>(64).also {
                            val updates = resource.update.filterNotNull()
                            for (update in updates) {
                                it.add(update)
                            }
                        }

                        size++

                        if (size >= ResourceStoreBucket.STORE_SIZE) {
                            expand(loadRequired = true, hasLock = true).load(id[ResourceStoreBucket.STORE_SIZE - 1])
                            break
                        }
                    }
                }
            }
        }
    }

    override suspend fun saveResources(bucket: ResourceStoreBucket<Unit>, indices: IntArray, len: Int) {
        // OK
    }

    override suspend fun loadProviderIndex(
        type: String,
        providerId: String,
        register: suspend (uid: Int, pid: Int) -> Unit
    ) {
        mutex.withLock {
            val productIds = products.productProviderToProductIds(providerId) ?: emptyList()
            for (resource in initialResources) {
                if (resource.product in productIds) {
                    register(
                        if (resource.project == 0) resource.createdBy else 0,
                        resource.project
                    )
                }
            }
        }
    }

    override suspend fun loadIdIndex(
        type: String,
        minimumId: Long,
        maximumIdExclusive: Long,
        register: (id: Long, ref: Int, isUser: Boolean) -> Unit
    ) {
        mutex.withLock {
            for (resource in initialResources) {
                if (resource.id in minimumId..<maximumIdExclusive) {
                    register(
                        resource.id,
                        resource.project.takeIf { it != 0 } ?: resource.createdBy,
                        resource.project == 0
                    )
                }
            }
        }
    }

    override suspend fun loadMaximumId(): Long {
        return mutex.withLock {
            initialResources.maxByOrNull { it.id }?.id ?: 1L
        }
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
                override suspend fun loadState(ctx: DBContext, count: Int, resources: LongArray): Array<Unit> {
                    return Array(count) {}
                }

                override suspend fun saveState(
                    ctx: DBContext,
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

        queries.initialResources.add(
            ResourceDocument(
                id = adminOnlyResource,
                createdBy = admin.uid,
                project = project,
                product = product,
                data = Unit,
            )
        )

        queries.initialResources.add(
            ResourceDocument(
                id = adminOnlyByMember,
                createdBy = member.uid,
                project = project,
                product = product,
                data = Unit,
            )
        )

        queries.initialResources.add(
            ResourceDocument(
                id = memberByAdminViaGroup,
                createdBy = admin.uid,
                project = project,
                product = product,
                data = Unit,
            ).apply {
                acl[0] = ResourceDocument.AclEntry(group, false, Permission.READ)
            }
        )

        queries.initialResources.add(
            ResourceDocument(
                id = memberByMemberViaAcl,
                createdBy = member.uid,
                project = project,
                product = product,
                data = Unit,
            ).apply {
                acl[0] = ResourceDocument.AclEntry(member.uid, true, Permission.READ)
            }
        )

        queries.initialResources.add(
            ResourceDocument(
                id = personalResource,
                createdBy = member.uid,
                project = 0,
                product = product,
                data = Unit,
                acl = arrayOf(ResourceDocument.AclEntry(member.uid, true, Permission.READ))
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
            ids.add(store.create(
                idCard,
                ref,
                Unit,
                null,
            ))
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

    private suspend fun TestContext.testBrowse(
        idCard: IdCard,
        ids: ArrayList<Long>
    ) {
        val dirs = listOf(SortDirection.ascending, SortDirection.descending)
        for (dir in dirs) {
            val collectedIds = ArrayList<Long>()
            var next: String? = null
            var shouldBreak = false
            while (!shouldBreak) {
                ResourceOutputPool.withInstance<Unit, Unit> { pool ->
                    val res = store.browse(
                        idCard,
                        pool,
                        next,
                        SimpleResourceIncludeFlags(),
                        sortDirection = dir,
                    )

                    for (i in 0 until res.count) {
                        collectedIds.add(pool[i].id)
                    }

                    next = res.next
                    if (next == null) shouldBreak = true
                }
            }

            assertEquals(ids.sorted(), collectedIds.sorted())
        }
    }

    @Test
    fun `test many workspaces concurrent`() = test {
        val workspaceCount = 50_000
        val resourcePerWorkspace = 2000
        val product = products.insert("a", "b", "c")
        val ref = products.productIdToReference(product)!!

        coroutineScope {
            // TODO There is some performance issue here. CPU usage is also extremely low despite spinning up many
            //   concurrent threads.
            (0..<workspaceCount).chunked(Runtime.getRuntime().availableProcessors()).forEach { chunk ->
                chunk.map { i ->
                    launch(Dispatchers.IO) {
                        val user = createUser("user$i")
                        val idCard = user.idCard()
                        val ids = ArrayList<Long>()
                        val start = System.nanoTime()
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
                        val end = System.nanoTime()
                        println("Time to create: ${end - start}")

                        testBrowse(idCard, ids)
                    }
                }.joinAll()
                println("Block complete!")
            }
        }
    }
}
