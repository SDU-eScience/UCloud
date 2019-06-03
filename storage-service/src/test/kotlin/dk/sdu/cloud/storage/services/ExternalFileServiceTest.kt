package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FileScanner
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.storage.util.createDummyFSInRoot
import dk.sdu.cloud.storage.util.createFS
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
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
        val service: FileScanner<Ctx>
    ) {
        val collectedEvents: List<StorageEvent>
            get() = EventServiceMock.messagesForTopic(StorageEvents.events)
    }

    fun createService(builder: File.() -> Unit = File::createDummyFSInRoot): TestContext<LinuxFSRunner> {
        return createService(createFS(builder))
    }

    fun createService(root: String): TestContext<LinuxFSRunner> {
        EventServiceMock.reset()
        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        val fileSensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        val coreFs = CoreFileSystemService(fs, mockk(relaxed = true), fileSensitivityService, ClientMock.authenticatedClient)
        val eventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})

        return TestContext(
            runner = runner,
            fs = coreFs,
            producer = eventProducer,
            service = FileScanner(runner, coreFs, eventProducer)
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
            assertEquals("/home/$fileName", fileCreatedEvent.file.path)
            assertEquals(FileType.FILE, fileCreatedEvent.file.fileType)
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
            assertEquals("/home/$fileName", fileCreatedEvent.file.path)
            assertEquals(FileType.DIRECTORY, fileCreatedEvent.file.fileType)
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

            val dirEvent = createdEvents.find { it.file.path.contains(dirName) }!!
            assertEquals("/home/$dirName", dirEvent.file.path)
            assertEquals(FileType.DIRECTORY, dirEvent.file.fileType)

            files.forEach { file ->
                val fileEvent = createdEvents.find { it.file.path == "/home/$dirName/$file" }!!
                assertEquals(FileType.FILE, fileEvent.file.fileType)
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

            val dirA = createdEvents.find { it.file.path == "/home/a" }!!
            assertEquals(FileType.DIRECTORY, dirA.file.fileType)

            val dirB = createdEvents.find { it.file.path == "/home/a/b" }!!
            assertEquals(FileType.DIRECTORY, dirB.file.fileType)

            val fileC = createdEvents.find { it.file.path == "/home/a/b/c" }!!
            assertEquals(FileType.FILE, fileC.file.fileType)
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
