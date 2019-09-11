package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.acl.AccessRights
import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.createDummyFS
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.initializeMicro
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

class WorkspaceTest {
    private lateinit var micro: Micro
    private lateinit var aclService: AclService<*>
    private lateinit var workspaceService: WorkspaceService<LinuxFSRunner>
    private lateinit var fsRoot: File

    @BeforeTest
    fun initializeTest() {
        micro = initializeMicro()
        micro.install(HibernateFeature)

        BackgroundScope.init()
        aclService = AclService(micro.hibernateDatabase, AclHibernateDao(), MockedHomeFolderService, { it.normalize() })
        fsRoot = createDummyFS()
        val (runner, fs) = linuxFSWithRelaxedMocks(fsRoot.absolutePath)

        val eventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), { throw it })

        val coreFileSystem = CoreFileSystemService(
            fs,
            eventProducer,
            FileSensitivityService(fs, eventProducer),
            ClientMock.authenticatedClient
        )

        val fileScanner = FileScanner(
            runner,
            coreFileSystem,
            eventProducer
        )

        workspaceService = WorkspaceService(fsRoot, fileScanner, aclService, coreFileSystem, runner)
        BackgroundScope.stop()
    }

    @Test
    fun `test creating workspace with no mounts`() = runBlocking {
        val response = workspaceService.create("user", emptyList(), false, "/input")
        assertThatInstance(response) { it.failures.isEmpty() }
        workspaceService.transfer(response.workspaceId, emptyList(), "/home/user/foo", true)
        workspaceService.delete(response.workspaceId)
    }

    @Test
    fun `test creating workspace with mounts`() = runBlocking {
        val response = workspaceService.create(
            "user",
            listOf(
                WorkspaceMount("/home/user/folder", "foo")
            ),
            false,
            "/input"
        )
        assertThatInstance(response) { it.failures.isEmpty() }
        workspaceService.transfer(response.workspaceId, emptyList(), "/home/user/folder", true)
        workspaceService.delete(response.workspaceId)
    }

    @Test(expected = FSException.PermissionException::class)
    fun `test creating workspace without permissions`() = runBlocking {
        workspaceService.create(
            "user2",
            listOf(
                WorkspaceMount("/home/user/folder", "foo")
            ),
            false,
            "/input"
        )

        return@runBlocking
    }

    @Test
    fun `test creating workspace with permissions`() = runBlocking {
        val path = "/home/user/folder"
        val username = "user2"
        aclService.updatePermissions(path, username, AccessRights.READ_WRITE)
        workspaceService.create(
            username,
            listOf(
                WorkspaceMount(path, "foo")
            ),
            false,
            "/input"
        )

        return@runBlocking
    }

    @Test
    fun `test workspace with permissions - transfer`() = runBlocking {
        val path = "/home/user/folder"
        val username = "user2"
        aclService.updatePermissions(path, username, AccessRights.READ_WRITE)
        val creationResponse = workspaceService.create(
            username,
            listOf(
                WorkspaceMount(path, "foo")
            ),
            false,
            "/input"
        )

        File(fsRoot, "/workspace/${creationResponse.workspaceId}/output/foo/qwe").writeText("Hello!")

        assertThatInstance(
            workspaceService.transfer(creationResponse.workspaceId, listOf("*"), "/home/$username/files", true),
            matcher = { it.isNotEmpty() }
        )
    }

    @Test
    fun `test workspace without permissions - transfer (merge)`() = runBlocking {
        val path = "/home/user/folder"
        val username = "user2"
        aclService.updatePermissions(path, username, AccessRights.READ_WRITE)
        val creationResponse = workspaceService.create(
            username,
            listOf(
                WorkspaceMount(path, "foo")
            ),
            false,
            "/input"
        )

        File(fsRoot, "/workspace/${creationResponse.workspaceId}/output/foo/qwe").writeText("Hello!")

        aclService.revokePermission(path, username)
        assertThatInstance(
            workspaceService.transfer(creationResponse.workspaceId, listOf("*"), "/home/user/files", true),
            matcher = { it.isEmpty() }
        )
    }

    @Test
    fun `test workspace without permissions - transfer (default)`() = runBlocking {
        val path = "/home/user/folder"
        val username = "user2"
        aclService.updatePermissions(path, username, AccessRights.READ_WRITE)
        val creationResponse = workspaceService.create(
            username,
            listOf(
                WorkspaceMount(path, "foo")
            ),
            false,
            "/input"
        )

        File(fsRoot, "/workspace/${creationResponse.workspaceId}/output/qwe").writeText("Hello!")

        assertThatInstance(
            workspaceService.transfer(creationResponse.workspaceId, listOf("*"), "/home/user/files", true),
            matcher = { it.isEmpty() }
        )
    }

    @Test
    fun `test workspace with some permissions - transfer`() = runBlocking {
        val path = "/home/user/folder"
        val username = "user2"
        aclService.updatePermissions(path, username, AccessRights.READ_WRITE)
        val creationResponse = workspaceService.create(
            username,
            listOf(
                WorkspaceMount(path, "foo")
            ),
            false,
            "/input"
        )

        // We keep permissions in the foo mount but not in the destination dir. The result should be a single file
        // transferred.
        File(fsRoot, "/workspace/${creationResponse.workspaceId}/output/foo/qwe").writeText("Hello!")
        File(fsRoot, "/workspace/${creationResponse.workspaceId}/output/qwe").writeText("Hello!")

        assertThatInstance(
            workspaceService.transfer(creationResponse.workspaceId, listOf("*"), "/home/user/files", true),
            matcher = { it.size == 1 }
        )
    }

    @Test
    fun `test workspace transfer of deleted file on root`() = runBlocking {
        val path = "/home/user/folder"
        val fileToDeleteName = "fileToDelete"
        val fileToDelete = "/home/user/folder/$fileToDeleteName"
        val mountPath = "mount"
        File(fsRoot, ".$fileToDelete").writeText("This will be deleted")

        val creationResponse = workspaceService.create(
            "user",
            listOf(
                WorkspaceMount(path, mountPath, readOnly = false)
            ),
            false,
            "/input"
        )


        // We keep permissions in the foo mount but not in the destination dir. The result should be a single file
        // transferred.
        val workspaceRoot = File(fsRoot, "workspace/${creationResponse.workspaceId}/output/$mountPath")
        File(workspaceRoot, "qwe").writeText("Hello!")
        File(workspaceRoot, fileToDeleteName).delete()

        workspaceService.transfer(creationResponse.workspaceId, listOf("*"), path, true)

        // Check if the files were deleted correctly from the cloud

        assertFalse(File(fsRoot, fileToDelete).exists())
    }

    @Test
    fun `test workspace transfer of deleted file in folder`() = runBlocking {
        val path = "/home/user/folder"
        val fileToDeleteName = "fileToDelete"
        val fileToDelete = "/home/user/folder/subfolder/$fileToDeleteName"
        val mountPath = "mount"
        File(fsRoot, ".$fileToDelete").writeText("This will be deleted")

        val creationResponse = workspaceService.create(
            "user",
            listOf(
                WorkspaceMount(path, mountPath, readOnly = false)
            ),
            false,
            "/input"
        )

        // We keep permissions in the foo mount but not in the destination dir. The result should be a single file
        // transferred.
        val workspaceRoot = File(fsRoot, "workspace/${creationResponse.workspaceId}/output/$mountPath")
        File(workspaceRoot, "qwe").writeText("Hello!")
        File(workspaceRoot, "subfolder/$fileToDeleteName").delete()

        workspaceService.transfer(creationResponse.workspaceId, listOf("*"), path, true)

        // Check if the file was deleted correctly from the cloud
        assertFalse(File(fsRoot, fileToDelete).exists())
    }
}
