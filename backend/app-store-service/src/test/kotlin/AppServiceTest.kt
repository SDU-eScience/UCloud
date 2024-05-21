package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.util.IProjectCache
import dk.sdu.cloud.accounting.util.MembershipStatusCacheEntry
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.service.db.async.DiscardingDBContext
import kotlinx.coroutines.runBlocking
import org.cliffc.high_scale_lib.NonBlockingHashMap
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*

private const val DUMMY_TOOL = "tool"
private const val DUMMY_APP = "app"

class AppServiceTest {
    data class Services(
        val service: AppService,
        val cache: FakeProjectCache,
    )

    class FakeProjectCache : IProjectCache {
        val entries = NonBlockingHashMap<String, MembershipStatusCacheEntry>()
        override suspend fun lookup(username: String): MembershipStatusCacheEntry {
            return entries[username] ?: MembershipStatusCacheEntry(username, emptyList(), emptyList())
        }

        override suspend fun invalidate(username: String) {
            entries.remove(username)
        }
    }

    fun createService(): Services {
        val cache = FakeProjectCache()
        return Services(
            AppService(DiscardingDBContext, cache, AuthenticatedClient(RpcClient(), OutgoingHttpCall, {}, {})),
            cache,
        )
    }

    fun createTool(
        name: String,
        age: Int = 1,
        title: String = name,
        description: String = name,
    ): Tool {
        return Tool(
            "_ucloud",
            age.toLong(),
            age.toLong(),
            NormalizedToolDescription(
                NameAndVersion(name, age.toString()),
                "container:1234",
                1,
                SimpleDuration(1, 0, 0),
                emptyList(),
                listOf("UCloud"),
                title,
                description,
                ToolBackend.DOCKER,
                "license",
                "container:1234",
                null,
            )
        )
    }

    fun createApp(
        name: String,
        age: Int,
        tool: NameAndVersion,
        title: String = name,
        description: String = name,
        extensions: List<String> = emptyList(),
    ): Application {
        return Application(
            ApplicationMetadata(
                name,
                age.toString(),
                listOf("UCloud"),
                title,
                description,
                null,
                false,
                null,
                null,
                age.toLong(),
            ),
            ApplicationInvocationDescription(
                ToolReference(tool.name, tool.version),
                listOf(WordInvocationParameter("runcode")),
                emptyList(),
                listOf("*"),
                fileExtensions = extensions,
            )
        )
    }

    fun actor(username: String) = ActorAndProject(Actor.SystemOnBehalfOfUser(username), null)

    inline fun withTest(crossinline block: suspend Services.() -> Unit) {
        runBlocking {
            createService().block()
        }
    }

    suspend fun Services.createDummyAppAndTool(
        prefix: String = "",
        public: Boolean = false,
        version: Int = 1
    ): Pair<Tool, Application> {
        val tool = createTool(prefix + DUMMY_TOOL, version)
        service.createTool(ActorAndProject.System, tool)
        val app = createApp(prefix + DUMMY_APP, version, tool.description.info)
        service.createApplication(ActorAndProject.System, app)
        if (public) {
            service.updatePublicFlag(
                ActorAndProject.System,
                NameAndVersion(prefix + DUMMY_APP, version.toString()),
                true
            )
        }

        return Pair(tool, app)
    }

    @Test
    fun `test creation and retrieval`() = withTest {
        val (tool, app) = createDummyAppAndTool()

        val retrievedTool = service.retrieveTool(ActorAndProject.System, DUMMY_TOOL, "1")
        assertNotNull(retrievedTool)
        assertEquals(tool.description.info.version, retrievedTool.description.info.version)
        assertEquals(tool.description.description, retrievedTool.description.description)

        val retrievedApp = service.retrieveApplication(ActorAndProject.System, DUMMY_APP, "1")
        assertNotNull(retrievedApp)
        assertEquals(app.metadata.description, retrievedApp.metadata.description)
        assertEquals(app.metadata.version, retrievedApp.metadata.version)

        val listOfTools = service.listToolVersions(ActorAndProject.System, DUMMY_TOOL)
        val singleTool = listOfTools.singleOrNull()
        assertNotNull(singleTool)
        assertEquals(tool.description.info.version, singleTool.description.info.version)
        assertEquals(tool.description.description, singleTool.description.description)

        val listOfApps = service.listVersions(ActorAndProject.System, DUMMY_APP)
        val singleApp = listOfApps.singleOrNull()
        assertNotNull(singleApp)
        assertEquals(app.metadata.description, singleApp.metadata.description)
        assertEquals(app.metadata.version, singleApp.metadata.version)
    }

    @Test
    fun `test sorting of versions`() = withTest {
        for (i in 1..10) {
            val (tool, app) = createDummyAppAndTool(version = i)

            val listOfTools = service.listToolVersions(ActorAndProject.System, DUMMY_TOOL)
            val newestTool = listOfTools.firstOrNull()
            assertNotNull(newestTool)
            assertEquals(tool.description.info.version, newestTool.description.info.version)

            val listOfApps = service.listVersions(ActorAndProject.System, DUMMY_APP)
            val newestApp = listOfApps.firstOrNull()
            assertNotNull(newestApp)
            assertEquals(app.metadata.description, newestApp.metadata.description)
        }
    }

    @Test
    fun `test access of non-public app fails`() = withTest {
        createDummyAppAndTool(public = false, version = 1)

        assertNotNull(service.retrieveApplication(ActorAndProject.System, DUMMY_APP, "1"))
        assertNull(service.retrieveApplication(actor("test"), DUMMY_APP, "1"))
        service.updatePublicFlag(ActorAndProject.System, NameAndVersion(DUMMY_APP, "1"), true)
        assertNotNull(service.retrieveApplication(actor("test"), DUMMY_APP, "1"))
    }

    @Test
    fun `test duplicate version fails`() = withTest {
        val (tool, app) = createDummyAppAndTool()

        assertFails { service.createTool(ActorAndProject.System, tool) }
        assertFails { service.createApplication(ActorAndProject.System, app) }
    }

    @Test
    fun `test upload fails as normal user`() = withTest {
        val tool = createTool(DUMMY_TOOL, 1)
        assertFails { service.createTool(actor("user"), tool) }
        service.createTool(ActorAndProject.System, tool)
        val app = createApp(DUMMY_APP, 1, tool.description.info)
        assertFails { service.createApplication(actor("user"), app) }
    }

    @Test
    fun `test bad reference fails`() = withTest {
        val app = createApp(DUMMY_APP, 1, NameAndVersion("doesnt", "exist"))
        assertFails { service.createApplication(ActorAndProject.System, app) }
    }

    @Test
    fun `test acl works`() = withTest {
        val username = "user"
        val actor = actor(username)
        val project = "project"
        val group = "group"

        val (tool, app) = createDummyAppAndTool(public = false, version = 1)

        // No acl
        assertNull(service.retrieveApplication(actor, DUMMY_APP, "1"))

        // Acl via username
        assertFails {
            service.updateAcl(
                actor,
                DUMMY_APP,
                listOf(ACLEntryRequest(AccessEntity(username), ApplicationAccessRight.LAUNCH))
            )
        }

        service.updateAcl(
            ActorAndProject.System,
            DUMMY_APP,
            listOf(ACLEntryRequest(AccessEntity(username), ApplicationAccessRight.LAUNCH))
        )
        assertNotNull(service.retrieveApplication(actor, DUMMY_APP, "1"))

        val retrievedAcl = service.retrieveAcl(ActorAndProject.System, DUMMY_APP)
        assertEquals(1, retrievedAcl.size)
        assertEquals(username, retrievedAcl.single().entity.user)
        assertNull(retrievedAcl.single().entity.project)
        assertNull(retrievedAcl.single().entity.group)

        // Revoke username
        service.updateAcl(
            ActorAndProject.System,
            DUMMY_APP,
            listOf(ACLEntryRequest(AccessEntity(username), ApplicationAccessRight.LAUNCH, revoke = true))
        )
        assertNull(service.retrieveApplication(actor, DUMMY_APP, "1"))

        // Via project admin
        cache.entries[username] = MembershipStatusCacheEntry(
            username,
            listOf(project),
            emptyList(),
        )

        service.updateAcl(
            ActorAndProject.System,
            DUMMY_APP,
            listOf(
                ACLEntryRequest(
                    AccessEntity(project = project, group = group),
                    ApplicationAccessRight.LAUNCH,
                    revoke = false
                )
            )
        )
        assertNotNull(service.retrieveApplication(actor, DUMMY_APP, "1"))

        service.updateAcl(
            ActorAndProject.System,
            DUMMY_APP,
            listOf(
                ACLEntryRequest(
                    AccessEntity(project = project, group = group),
                    ApplicationAccessRight.LAUNCH,
                    revoke = true
                )
            )
        )
        assertNull(service.retrieveApplication(actor, DUMMY_APP, "1"))

        // Via project group
        cache.entries[username] = MembershipStatusCacheEntry(
            username,
            emptyList(),
            listOf(MembershipStatusCacheEntry.GroupMemberOf(project, group))
        )

        service.updateAcl(
            ActorAndProject.System,
            DUMMY_APP,
            listOf(
                ACLEntryRequest(
                    AccessEntity(project = project, group = group),
                    ApplicationAccessRight.LAUNCH,
                    revoke = false
                )
            )
        )
        assertNotNull(service.retrieveApplication(actor, DUMMY_APP, "1"))

        service.updateAcl(
            ActorAndProject.System,
            DUMMY_APP,
            listOf(
                ACLEntryRequest(
                    AccessEntity(project = project, group = group),
                    ApplicationAccessRight.LAUNCH,
                    revoke = true
                )
            )
        )
        assertNull(service.retrieveApplication(actor, DUMMY_APP, "1"))
    }

    @Test
    fun `test pre-release versions`() = withTest {
        val (tool) = createDummyAppAndTool()

        val app2 = createApp(DUMMY_APP, 2, tool.description.info)
        service.createApplication(ActorAndProject.System, app2)

        service.updatePublicFlag(ActorAndProject.System, NameAndVersion(DUMMY_APP, "1"), true)

        assertEquals(2, service.listVersions(ActorAndProject.System, DUMMY_APP).size)
        assertEquals("1", service.listVersions(actor("user"), DUMMY_APP).singleOrNull()?.metadata?.version)
    }

    @Test
    fun `test retrieve latest`() = withTest {
        for (i in 1..10) {
            val tool = createTool(DUMMY_TOOL, i)
            service.createTool(ActorAndProject.System, tool)
            val app = createApp(DUMMY_APP, i, tool.description.info)
            service.createApplication(ActorAndProject.System, app)

            val newestTool = service.retrieveTool(ActorAndProject.System, DUMMY_TOOL, null)
            assertNotNull(newestTool)
            assertEquals(tool.description.info.version, newestTool.description.info.version)

            val newestApp = service.retrieveApplication(ActorAndProject.System, DUMMY_APP, null)
            assertNotNull(newestApp)
            assertEquals(app.metadata.description, newestApp.metadata.description)
        }
    }

    @Test
    fun `test retrieve latest with pre-release version`() = withTest {
        val tool = createTool(DUMMY_TOOL, 1)
        service.createTool(ActorAndProject.System, tool)
        val app = createApp(DUMMY_APP, 1, tool.description.info)
        service.createApplication(ActorAndProject.System, app)

        val app2 = createApp(DUMMY_APP, 2, tool.description.info)
        service.createApplication(ActorAndProject.System, app2)

        service.updatePublicFlag(ActorAndProject.System, NameAndVersion(DUMMY_APP, "1"), true)

        run {
            val newestApp = service.retrieveApplication(ActorAndProject.System, DUMMY_APP, null)
            assertNotNull(newestApp)
            assertEquals(app2.metadata.description, newestApp.metadata.description)
        }

        run {
            val newestApp = service.retrieveApplication(actor("user"), DUMMY_APP, null)
            assertNotNull(newestApp)
            assertEquals(app.metadata.description, newestApp.metadata.description)
        }
    }

    @Test
    fun `test various extensions are picked up`() = withTest {
        suspend fun checkFile(actorAndProject: ActorAndProject, file: String, name: String, version: String) {
            val cppApp = service.listByExtension(actorAndProject, listOf(file)).singleOrNull()
            assertNotNull(cppApp)
            assertEquals(cppApp.metadata.name, name)
            assertEquals(cppApp.metadata.version, version)
        }

        val tool = createTool(DUMMY_TOOL, 1)
        service.createTool(ActorAndProject.System, tool)

        val normalApp = createApp(DUMMY_APP, 1, tool.description.info)
        service.createApplication(ActorAndProject.System, normalApp)

        val codeApp = createApp("code", 1, tool.description.info, extensions = listOf(".cpp"))
        service.createApplication(ActorAndProject.System, codeApp)

        val codeApp2 = createApp("code", 2, tool.description.info, extensions = listOf(".cpp", ".c"))
        service.createApplication(ActorAndProject.System, codeApp2)

        val dockerfileApp = createApp("docker", 1, tool.description.info, extensions = listOf("Dockerfile"))
        service.createApplication(ActorAndProject.System, dockerfileApp)

        val directoryApp = createApp("workflows", 1, tool.description.info, extensions = listOf("Workflows/"))
        service.createApplication(ActorAndProject.System, directoryApp)

        checkFile(ActorAndProject.System, "/1337/file.cpp", "code", "2")
        checkFile(ActorAndProject.System, "/1337/file.c", "code", "2")
        checkFile(ActorAndProject.System, "/1337/Dockerfile", "docker", "1")
        checkFile(ActorAndProject.System, "/1337/Workflows/", "workflows", "1")

        service.updatePublicFlag(ActorAndProject.System, NameAndVersion("code", "1"), true)
        checkFile(actor("user"), "/1337/file.cpp", "code", "1")

        service.updatePublicFlag(ActorAndProject.System, NameAndVersion("code", "2"), true)
        checkFile(actor("user"), "/1337/file.cpp", "code", "2")
    }

    @Test
    fun `test retrieve latest non-public`() = withTest {
        val actor = actor("user")

        val tool = createTool(DUMMY_TOOL, 1)
        service.createTool(ActorAndProject.System, tool)
        val app = createApp(DUMMY_APP, 1, tool.description.info)
        service.createApplication(ActorAndProject.System, app)
        service.updatePublicFlag(ActorAndProject.System, NameAndVersion(DUMMY_APP, "1"), true)

        assertEquals("1", service.retrieveApplication(actor, DUMMY_APP, null)?.metadata?.version)

        val app2 = createApp(DUMMY_APP, 2, tool.description.info)
        service.createApplication(ActorAndProject.System, app2)

        assertEquals("2", service.retrieveApplication(ActorAndProject.System, DUMMY_APP, null)?.metadata?.version)
        assertEquals("1", service.retrieveApplication(actor, DUMMY_APP, null)?.metadata?.version)
    }

    @Test
    fun `test stars`() = withTest {
        val actor = actor("user")

        val tool = createTool(DUMMY_TOOL, 1)
        service.createTool(ActorAndProject.System, tool)
        val app = createApp(DUMMY_APP, 1, tool.description.info)
        service.createApplication(ActorAndProject.System, app)
        service.updatePublicFlag(ActorAndProject.System, NameAndVersion(DUMMY_APP, "1"), true)

        service.toggleStar(actor, DUMMY_APP)
        val star1 = service.listStarredApplications(actor).singleOrNull()
        assertNotNull(star1)
        assertEquals("1", star1.metadata.version)

        // Creating a new version of the starred application should automatically upgrade us to the latest version
        val app2 = createApp(DUMMY_APP, 2, tool.description.info)
        service.createApplication(ActorAndProject.System, app2)


        val star2 = service.listStarredApplications(actor).singleOrNull()
        assertNotNull(star2)
        assertEquals("1", star1.metadata.version)

        service.updatePublicFlag(ActorAndProject.System, NameAndVersion(DUMMY_APP, "2"), true)

        val star3 = service.listStarredApplications(actor).singleOrNull()
        assertNotNull(star3)
        assertEquals("2", star3.metadata.version)

        assertEquals(true, service.retrieveApplication(actor, DUMMY_APP, "1")?.favorite)
        assertEquals(true, service.retrieveApplication(actor, DUMMY_APP, "2")?.favorite)

        service.setStar(actor, true, DUMMY_APP)
        assertEquals(true, service.retrieveApplication(actor, DUMMY_APP, "2")?.favorite)

        service.setStar(actor, false, DUMMY_APP)
        assertEquals(false, service.retrieveApplication(actor, DUMMY_APP, "2")?.favorite)

        service.setStar(actor, false, DUMMY_APP)
        assertEquals(false, service.retrieveApplication(actor, DUMMY_APP, "2")?.favorite)

        service.setStar(actor, true, DUMMY_APP)
        assertEquals(true, service.retrieveApplication(actor, DUMMY_APP, "2")?.favorite)
    }

    @Test
    fun `test search`() = withTest {
        val actor = actor("user")

        val tool = createTool(DUMMY_TOOL, 1)
        service.createTool(ActorAndProject.System, tool)

        val applicationNames = listOf("Visual Studio Code", "JupyterLab", "RStudio", "Terminal", "Ubuntu", "MinIO")
        for (i in 1..2) {
            for (appName in applicationNames) {
                val app = createApp(appName, i, tool.description.info)
                service.createApplication(ActorAndProject.System, app)
                service.updatePublicFlag(ActorAndProject.System, NameAndVersion(appName, i.toString()), i == 1)
            }

            assertEquals("Visual Studio Code", service.search(actor, "VS Code").firstOrNull()?.metadata?.name)
            assertEquals("JupyterLab", service.search(actor, "jupytlb").firstOrNull()?.metadata?.name)
            assertEquals("MinIO", service.search(actor, "iNIo workshop").firstOrNull()?.metadata?.name)
            assertEquals("Visual Studio Code", service.search(actor, "studio de").firstOrNull()?.metadata?.name)
            val studioSet = service.search(actor, "studio").take(2).map { it.metadata.name }.toSet()
            assertTrue("Visual Studio Code" in studioSet)
            assertTrue("RStudio" in studioSet)
        }
    }

    @Test
    fun `test logo upload`() = withTest {
        fun createLogo(width: Int, height: Int): ByteArray {
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.background = Color.RED
            g.clearRect(0, 0, width, height)
            val bos = ByteArrayOutputStream()
            ImageIO.write(img, "PNG", bos)
            return bos.toByteArray()
        }

        val groupId = service.createGroup(ActorAndProject.System, "Application Group")
        assertNull(service.retrieveGroupLogo(groupId))

        // Small logos should be preserved as is
        val smallLogo = createLogo(10, 10)
        service.updateGroup(ActorAndProject.System, groupId, newLogo = smallLogo)
        assertTrue(smallLogo.contentEquals(service.retrieveGroupLogo(groupId)))

        // Large logos should be resized to a smaller logo
        val largeLogo = createLogo(2000, 2000)
        service.updateGroup(ActorAndProject.System, groupId, newLogo = largeLogo)
        val newLogo = service.retrieveGroupLogo(groupId)

        val newLogoImg = ImageIO.read(ByteArrayInputStream(newLogo))
        assertEquals(255, (newLogoImg.getRGB(0, 0) shr 16) and 0xFF) // It should be red
        assertTrue(newLogoImg.height > 100)
        assertTrue(newLogoImg.width > 100)
        assertTrue(newLogoImg.width < 2000)
        assertTrue(newLogoImg.height < 2000)

        val invalidLogo = ByteArray(1000) { 0xDE.toByte() }
        assertFails { service.updateGroup(ActorAndProject.System, groupId, newLogo = invalidLogo) }
        assertNotNull(service.retrieveGroupLogo(groupId))

        service.updateGroup(ActorAndProject.System, groupId, newLogo = ByteArray(0))
        assertNull(service.retrieveGroupLogo(groupId))
    }

    @Test
    fun `test group creation and updates`() = withTest {
        val groupTitle = "title"
        val description = "test description"
        val flavorName = "flavor"
        val (tool, app) = createDummyAppAndTool(public = true)
        createDummyAppAndTool(version = 2)
        createDummyAppAndTool(version = 3)

        service.updateAppFlavorName(ActorAndProject.System, app.metadata.name, flavorName)
        val groupId = service.createGroup(ActorAndProject.System, groupTitle)
        service.assignApplicationToGroup(ActorAndProject.System, DUMMY_APP, groupId)
        service.updateGroup(
            ActorAndProject.System,
            groupId,
            newDescription = description,
            newDefaultFlavor = flavorName
        )

        fun checkGroup(group: ApplicationGroup?) {
            assertEquals(groupId, group?.metadata?.id)
            assertEquals(groupTitle, group?.specification?.title)
            assertEquals(description, group?.specification?.description)
            assertEquals(flavorName, group?.specification?.defaultFlavor)
        }

        val groupThroughListing = service.listGroups(ActorAndProject.System).singleOrNull()
        assertNotNull(groupThroughListing)
        checkGroup(groupThroughListing)

        val appThroughRetrieve =
            service.retrieveApplication(ActorAndProject.System, app.metadata.name, app.metadata.version)
        assertNotNull(appThroughRetrieve)
        assertEquals(flavorName, appThroughRetrieve.metadata.flavorName)
        checkGroup(appThroughRetrieve.metadata.group)

        for (appThroughList in service.listVersions(ActorAndProject.System, app.metadata.name)) {
            assertEquals(flavorName, appThroughList.metadata.flavorName)
            checkGroup(appThroughList.metadata.group)
        }

        val appsThroughCategory = service.listApplicationsInGroup(ActorAndProject.System, groupId)
        assertEquals(1, appsThroughCategory.size)
        for (appThroughList in appsThroughCategory) {
            assertEquals(flavorName, appThroughList.metadata.flavorName)
            checkGroup(appThroughList.metadata.group)
        }

        val retrievedGroup = service.retrieveGroup(ActorAndProject.System, groupId)
        checkGroup(retrievedGroup)

        val secondGroup = service.createGroup(ActorAndProject.System, "New group")
        service.assignApplicationToGroup(ActorAndProject.System, app.metadata.name, secondGroup)

        val appThroughRetrieve2 =
            service.retrieveApplication(ActorAndProject.System, app.metadata.name, app.metadata.version)
        assertNotNull(appThroughRetrieve2)
        assertEquals(flavorName, appThroughRetrieve2.metadata.flavorName) // flavor name should be retained
        assertNull(app.metadata.group) // group should not be retained

        val appsThroughCategory2 = service.listApplicationsInGroup(ActorAndProject.System, groupId)
        assertEquals(0, appsThroughCategory2.size)

        service.deleteGroup(ActorAndProject.System, secondGroup)
        assertNull(service.retrieveGroup(ActorAndProject.System, secondGroup))
        assertNull(service.retrieveApplication(ActorAndProject.System, app.metadata.name, app.metadata.version)!!.metadata.group)

        assertFails { service.assignApplicationToGroup(ActorAndProject.System, "invalid-application-name", groupId) }
    }

    @Test
    fun `test categories`() = withTest {
        val appGroups = (0..<10).map {
            val app = createDummyAppAndTool(prefix = "app-$it-", public = true).second
            val groupId = service.createGroup(ActorAndProject.System, "Group $it")
            service.assignApplicationToGroup(ActorAndProject.System, app.metadata.name, groupId)

            groupId to app
        }

        val categoryName = "Category"
        val category = service.createCategory(ActorAndProject.System, ApplicationCategory.Specification(categoryName))
        for ((groupId) in appGroups) {
            service.addGroupToCategory(ActorAndProject.System, listOf(category), groupId)
        }

        val categoryThroughList = service.listCategories().singleOrNull()
        assertNotNull(categoryThroughList)
        assertEquals(categoryName, categoryThroughList.specification.title)

        val (retrievedCategoryTitle, retrievedApps) = service.listApplicationsInCategory(
            ActorAndProject.System,
            categoryThroughList.metadata.id
        )

        assertEquals(appGroups.size, retrievedApps.size)
        assertEquals(categoryName, retrievedCategoryTitle)
        for ((_, app) in appGroups) {
            assertNotNull(retrievedApps.find { it.metadata.name == app.metadata.name })
        }

        service.assignApplicationToGroup(ActorAndProject.System, appGroups[0].second.metadata.name, null)

        val (_, retrievedApps2) = service.listApplicationsInCategory(
            ActorAndProject.System,
            categoryThroughList.metadata.id
        )
        assertEquals(appGroups.size - 1, retrievedApps2.size)
        for ((_, app) in appGroups) {
            if (app.metadata.name == appGroups[0].second.metadata.name) continue
            assertNotNull(retrievedApps.find { it.metadata.name == app.metadata.name })
        }
    }

    @Test
    fun `test multiple flavors with implicit name`() = withTest {
        // NOTE(Dan): Added in random order to ensure that we sort the flavors alphabetically
        val (_, appD) = createDummyAppAndTool(prefix = "D", public = true)
        val (_, appB) = createDummyAppAndTool(prefix = "B", public = true)
        val (_, appA) = createDummyAppAndTool(prefix = "A", public = true)
        val (_, appC) = createDummyAppAndTool(prefix = "C", public = true)

        val groupId = service.createGroup(ActorAndProject.System, "Group")
        service.assignApplicationToGroup(ActorAndProject.System, appD.metadata.name, groupId)
        service.assignApplicationToGroup(ActorAndProject.System, appB.metadata.name, groupId)
        service.assignApplicationToGroup(ActorAndProject.System, appA.metadata.name, groupId)
        service.assignApplicationToGroup(ActorAndProject.System, appC.metadata.name, groupId)

        val apps = service.listApplicationsInGroup(ActorAndProject.System, groupId)
        assertEquals(4, apps.size)
        assertEquals(appA.metadata.name, apps[0].metadata.name)
        assertEquals(appB.metadata.name, apps[1].metadata.name)
        assertEquals(appC.metadata.name, apps[2].metadata.name)
        assertEquals(appD.metadata.name, apps[3].metadata.name)
    }

    @Test
    fun `test multiple flavors with explicit name`() = withTest {
        // NOTE(Dan): Added in random order to ensure that we sort the flavors alphabetically
        val (_, appD) = createDummyAppAndTool(prefix = "D", public = true)
        val (_, appB) = createDummyAppAndTool(prefix = "B", public = true)
        val (_, appA) = createDummyAppAndTool(prefix = "A", public = true)
        val (_, appC) = createDummyAppAndTool(prefix = "C", public = true)

        service.updateAppFlavorName(ActorAndProject.System, appD.metadata.name, "D-Flavor")
        service.updateAppFlavorName(ActorAndProject.System, appB.metadata.name, "B-Flavor")
        service.updateAppFlavorName(ActorAndProject.System, appA.metadata.name, "A-Flavor")
        service.updateAppFlavorName(ActorAndProject.System, appC.metadata.name, "C-Flavor")

        val groupId = service.createGroup(ActorAndProject.System, "Group")
        service.assignApplicationToGroup(ActorAndProject.System, appD.metadata.name, groupId)
        service.assignApplicationToGroup(ActorAndProject.System, appB.metadata.name, groupId)
        service.assignApplicationToGroup(ActorAndProject.System, appA.metadata.name, groupId)
        service.assignApplicationToGroup(ActorAndProject.System, appC.metadata.name, groupId)

        val apps = service.listApplicationsInGroup(ActorAndProject.System, groupId)
        assertEquals(4, apps.size)
        assertEquals(appA.metadata.name, apps[0].metadata.name)
        assertEquals(appB.metadata.name, apps[1].metadata.name)
        assertEquals(appC.metadata.name, apps[2].metadata.name)
        assertEquals(appD.metadata.name, apps[3].metadata.name)
    }
}
