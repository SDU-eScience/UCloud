package dk.sdu.cloud.file.services

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.file.util.mkdir
import dk.sdu.cloud.file.util.touch
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CopyTest {
    val user = "user"

    data class TestContext<Ctx : FSUserContext>(
        val runner: LinuxFSRunnerFactory,
        val fs: LowLevelFileSystemInterface<Ctx>,
        val coreFs: CoreFileSystemService<Ctx>,
        val lookupService: FileLookupService<Ctx>,
        val sensitivityService: FileSensitivityService<Ctx>
    )

    private fun initTest(root: File): TestContext<FSUserContext> {
        BackgroundScope.init()

        val (runner, fs) = linuxFSWithRelaxedMocks(root.absolutePath)
        val storageEventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})
        val sensitivityService =
            FileSensitivityService(fs, storageEventProducer)
        val coreFs = CoreFileSystemService(fs, storageEventProducer, sensitivityService, ClientMock.authenticatedClient)
        val fileLookupService = FileLookupService(coreFs)

        return TestContext(runner, fs, coreFs, fileLookupService, sensitivityService) as TestContext<FSUserContext>
    }

    private fun createRoot(): File = Files.createTempDirectory("sensitivity-test").toFile()

    @Test
    fun `test copying a folder`() {

        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByLongId(1)
        )
        val root = createRoot()
        with(initTest(root)) {
            root.mkdir("home") {
                mkdir("user") {
                    mkdir("folder") {
                        touch("1")
                        touch("2")
                        mkdir("subfolder") {
                            touch("a")
                            touch("b")
                        }
                    }
                }
            }

            runner.withBlockingContext(user) { ctx ->
                coreFs.copy(ctx, "/home/user/folder", "/home/user/folder2", SensitivityLevel.PRIVATE, WriteConflictPolicy.REJECT)
                val mode = setOf(FileAttribute.PATH, FileAttribute.FILE_TYPE)
                val listing =
                    coreFs.listDirectory(ctx, "/home/user/folder2", mode)

                assertEquals(3, listing.size)
                assertThatInstance(listing) { it.any { it.path.fileName() == "1" } }
                assertThatInstance(listing) { it.any { it.path.fileName() == "2" } }
                assertThatInstance(listing) { it.any { it.path.fileName() == "subfolder" } }

                val sublisting = coreFs.listDirectory(ctx, "/home/user/folder2/subfolder", mode)
                assertEquals(2, sublisting.size)
                assertThatInstance(sublisting) { it.any { it.path.fileName() == "a" } }
                assertThatInstance(sublisting) { it.any { it.path.fileName() == "b" } }
            }
        }
    }

    @Test
    fun `test copying a folder (rename)`() {
        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByLongId(1)
        )

        val root = createRoot()
        with(initTest(root)) {
            root.mkdir("home") {
                mkdir("user") {
                    mkdir("folder") {
                        touch("1")
                        touch("2")
                        mkdir("subfolder") {
                            touch("a")
                            touch("b")
                        }
                    }
                }
            }

            runner.withBlockingContext(user) { ctx ->
                coreFs.copy(ctx, "/home/user/folder", "/home/user/folder", SensitivityLevel.PRIVATE , WriteConflictPolicy.RENAME)
                val mode = setOf(FileAttribute.PATH, FileAttribute.FILE_TYPE)

                val rootListing = coreFs.listDirectory(ctx, "/home/user", mode)
                assertEquals(2, rootListing.size)
                assertThatInstance(rootListing) { it.any { it.path.fileName() == "folder" } }
                assertThatInstance(rootListing) { it.any { it.path.fileName() == "folder(1)" } }

                val listing =
                    coreFs.listDirectory(ctx, "/home/user/folder(1)", mode)

                assertEquals(3, listing.size)
                assertThatInstance(listing) { it.any { it.path.fileName() == "1" } }
                assertThatInstance(listing) { it.any { it.path.fileName() == "2" } }
                assertThatInstance(listing) { it.any { it.path.fileName() == "subfolder" } }

                val sublisting = coreFs.listDirectory(ctx, "/home/user/folder(1)/subfolder", mode)
                assertEquals(2, sublisting.size)
                assertThatInstance(sublisting) { it.any { it.path.fileName() == "a" } }
                assertThatInstance(sublisting) { it.any { it.path.fileName() == "b" } }
            }
        }
    }
}
