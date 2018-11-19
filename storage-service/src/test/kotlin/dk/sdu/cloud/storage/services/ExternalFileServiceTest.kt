package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.ExternalFileService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.createDummyFSInRoot
import dk.sdu.cloud.storage.util.createFS
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class ExternalFileServiceTest {
    data class TestContext<Ctx : CommandRunner>(
        val runner: FSCommandRunnerFactory<Ctx>,
        val fs: CoreFileSystemService<Ctx>,
        val producer: StorageEventProducer,
        val service: ExternalFileService<Ctx>,
        val collectedEvents: MutableList<StorageEvent>
    )

    fun createService(builder: File.() -> Unit = File::createDummyFSInRoot): TestContext<UnixFSCommandRunner> {
        return createService(createFS(builder))
    }

    fun createService(root: String): TestContext<UnixFSCommandRunner> {
        val (runner, fs) = unixFSWithRelaxedMocks(root)
        val coreFs = CoreFileSystemService(fs, mockk(relaxed = true))
        val eventProducer = mockk<StorageEventProducer>(relaxed = true)
        val collectedEvents = ArrayList<StorageEvent>()

        coEvery { eventProducer.emit(capture(collectedEvents)) } just Runs

        return TestContext(
            runner = runner,
            fs = coreFs,
            producer = eventProducer,
            collectedEvents = collectedEvents,
            service = ExternalFileService(runner, coreFs, eventProducer)
        )
    }


    @Test
    fun `happy path - normal file`() {
        val fileName = "a"
        val ctx = createService {
            mkdir("/home") {
                touch(fileName)
            }
        }

        with(ctx) {
            runBlocking { service.scanFilesCreatedExternally("/home/$fileName") }
            assertEquals(1, collectedEvents.size)
            val fileCreatedEvent = collectedEvents.first() as StorageEvent.CreatedOrRefreshed
            assertEquals("/home/$fileName", fileCreatedEvent.path)
            assertEquals(FileType.FILE, fileCreatedEvent.fileType)
        }
    }

    @Test
    fun `happy path - empty directory`() {
        val fileName = "a"
        val ctx = createService {
            mkdir("home") {
                mkdir(fileName) {}
            }
        }

        with(ctx) {
            runBlocking { service.scanFilesCreatedExternally("/home/$fileName") }
            assertEquals(1, collectedEvents.size)
            val fileCreatedEvent = collectedEvents.first() as StorageEvent.CreatedOrRefreshed
            assertEquals("/home/$fileName", fileCreatedEvent.path)
            assertEquals(FileType.DIRECTORY, fileCreatedEvent.fileType)
        }
    }

    @Test
    fun `happy path - normal directory`() {
        val dirName = "directory"
        val files = listOf("a", "b", "c")

        val ctx = createService {
            mkdir("home") {
                mkdir(dirName) {
                    files.forEach { touch(it) }
                }
            }
        }

        with(ctx) {
            runBlocking { service.scanFilesCreatedExternally("/home/$dirName") }
            val createdEvents = collectedEvents.filterIsInstance<StorageEvent.CreatedOrRefreshed>()
            assertEquals(files.size + 1, collectedEvents.size)
            assertEquals(files.size + 1, createdEvents.size)

            val dirEvent = createdEvents.find { it.path.contains(dirName) }!!
            assertEquals("/home/$dirName", dirEvent.path)
            assertEquals(FileType.DIRECTORY, dirEvent.fileType)

            files.forEach { file ->
                val fileEvent = createdEvents.find { it.path == "/home/$dirName/$file" }!!
                assertEquals(FileType.FILE, fileEvent.fileType)
            }
        }
    }

    @Test
    fun `happy path - nested directory`() {
        val ctx = createService {
            mkdir("home") {
                mkdir("a") {
                    mkdir("b") {
                        touch("c")
                    }
                }
            }
        }

        with(ctx) {
            runBlocking { service.scanFilesCreatedExternally("/home/a") }
            val createdEvents = collectedEvents.filterIsInstance<StorageEvent.CreatedOrRefreshed>()
            assertEquals(3, collectedEvents.size)
            assertEquals(3, createdEvents.size)

            val dirA = createdEvents.find { it.path == "/home/a" }!!
            assertEquals(FileType.DIRECTORY, dirA.fileType)

            val dirB = createdEvents.find { it.path == "/home/a/b" }!!
            assertEquals(FileType.DIRECTORY, dirB.fileType)

            val fileC = createdEvents.find { it.path == "/home/a/b/c" }!!
            assertEquals(FileType.FILE, fileC.fileType)
        }
    }

    @Test
    fun `missing file`() {
        val ctx = createService {
            mkdir("home") {
                mkdir("someDir") {}
            }
        }

        with(ctx) {
            runBlocking { service.scanFilesCreatedExternally("/home/notSomeDir") }

            assertEquals(0, collectedEvents.size)
        }
    }
}
