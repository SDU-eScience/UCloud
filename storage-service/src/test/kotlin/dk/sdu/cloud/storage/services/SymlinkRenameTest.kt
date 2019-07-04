package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import io.mockk.MockK
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SymlinkRenameTest {
    val user = "user"

    data class TestContext(
        val runner: LinuxFSRunnerFactory,
        val fs: LowLevelFileSystemInterface<LinuxFSRunner>,
        val coreFs: CoreFileSystemService<LinuxFSRunner>,
        val lookupService: FileLookupService<LinuxFSRunner>,
        val sensitivityService: FileSensitivityService<LinuxFSRunner>
    )

    private fun initTest(root: File): TestContext {
        BackgroundScope.init()
        val cloud = ClientMock.authenticatedClient
        val (runner, fs) = linuxFSWithRelaxedMocks(root.absolutePath)
        val eventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})

        val sensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        val coreFs = CoreFileSystemService(fs, eventProducer, sensitivityService ,cloud)
        val fileLookupService = FileLookupService(coreFs)

        return TestContext(runner, fs, coreFs, fileLookupService, sensitivityService)
    }

    private fun createRoot(): File = Files.createTempDirectory("sensitivity-test").toFile()

    @Test
    fun `test buggy renaming`() {
        val root = createRoot()
        with(initTest(root)) {
            root.mkdir("home") {
                mkdir("user") {
                    touch("Foo")
                    touch("Foo(1)")
                }

                mkdir("user2") {}
            }

            runner.withBlockingContext(user) { ctx ->
                val created1 = coreFs.createSymbolicLink(ctx, "/home/user/Foo(1)", "/home/user2/Foo(1)")
                val created2 = coreFs.createSymbolicLink(ctx, "/home/user/Foo", "/home/user2/Foo")

                assertEquals("/home/user2/Foo(1)", created1.file.path)
                assertEquals("/home/user2/Foo", created2.file.path)

                val lookup = lookupService.listDirectory(ctx, "/home/user2", NormalizedPaginationRequest(null, null))
                assertThatProperty(lookup, { it.items.size }) { it == 2 }
                assertThatInstance(lookup) { it.items.map { it.path.fileName() }.contains("Foo") }
                assertThatInstance(lookup) { it.items.map { it.path.fileName() }.contains("Foo(1)") }
            }
        }
    }
}
