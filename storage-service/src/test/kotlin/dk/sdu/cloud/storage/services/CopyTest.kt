package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.services.BackgroundScope
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class CopyTest {
    val user = "user"

    data class TestContext(
        val runner: UnixFSCommandRunnerFactory,
        val fs: LowLevelFileSystemInterface<UnixFSCommandRunner>,
        val coreFs: CoreFileSystemService<UnixFSCommandRunner>,
        val sensitivityService: FileSensitivityService<UnixFSCommandRunner>,
        val lookupService: FileLookupService<UnixFSCommandRunner>
    )

    private fun initTest(root: File): TestContext {
        BackgroundScope.init()

        val (runner, fs) = unixFSWithRelaxedMocks(root.absolutePath)
        val storageEventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})
        val sensitivityService =
            FileSensitivityService(fs, storageEventProducer)
        val coreFs = CoreFileSystemService(fs, storageEventProducer)
        val fileLookupService = FileLookupService(coreFs)

        return TestContext(runner, fs, coreFs, sensitivityService, fileLookupService)
    }

    private fun createRoot(): File = Files.createTempDirectory("sensitivity-test").toFile()

    @Test
    fun `test copying a folder`() {
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
                coreFs.copy(ctx, "/home/user/folder", "/home/user/folder2", WriteConflictPolicy.REJECT)
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
                coreFs.copy(ctx, "/home/user/folder", "/home/user/folder", WriteConflictPolicy.RENAME)
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
