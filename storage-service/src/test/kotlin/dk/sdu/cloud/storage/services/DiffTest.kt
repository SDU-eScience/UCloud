package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.StorageFileImpl
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.ownSensitivityLevel
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.UIDLookupService
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.service.test.assertCollectionHasItem
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.storage.util.createFS
import dk.sdu.cloud.storage.util.inode
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.storageUserDaoWithFixedAnswer
import dk.sdu.cloud.storage.util.timestamps
import dk.sdu.cloud.storage.util.touch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val FILE_OWNER = "file creator"

class DiffTest {
    private data class TestingContext<Ctx : FSUserContext>(
        val fsRoot: File,
        val userDao: UIDLookupService,
        val fs: LowLevelFileSystemInterface<Ctx>,
        val mockedEventProducer: StorageEventProducer,
        val coreFs: CoreFileSystemService<Ctx>,
        val indexingService: IndexingService<Ctx>,
        val commandRunnerFactory: FSCommandRunnerFactory<Ctx>
    ) {
        fun File.asMaterialized(): StorageFileImpl =
            StorageFile(
                fileType = if (isDirectory) FileType.DIRECTORY else FileType.FILE,
                fileId = inode(),
                path = absolutePath.removePrefix(fsRoot.absolutePath).removePrefix("/").let { "/$it" },
                ownerName = FILE_OWNER,
                createdAt = timestamps().created,
                modifiedAt = timestamps().modified,
                size = length(),
                creator = FILE_OWNER,
                ownSensitivityLevel = null
            )
    }

    private fun ctx(
        consumer: TestingContext<LinuxFSRunner>.() -> Unit = {},
        builder: File.() -> Unit
    ): TestingContext<LinuxFSRunner> {
        EventServiceMock.reset()
        val userDao = storageUserDaoWithFixedAnswer(FILE_OWNER)
        val root = File(createFS(builder))
        val commandRunnerFactory = LinuxFSRunnerFactory(userDao)
        val cephFs = LinuxFS(commandRunnerFactory, root, userDao)
        val eventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})
        val coreFs = CoreFileSystemService(cephFs, eventProducer)
        val indexingService = IndexingService(commandRunnerFactory, coreFs, eventProducer)

        BackgroundScope.reset()

        return TestingContext(
            root,
            userDao,
            cephFs,
            eventProducer,
            coreFs,
            indexingService,
            commandRunnerFactory
        ).also(consumer)
    }

    private fun File.resolvePath(path: String): File = File(absolutePath + path)

    private fun fakeMaterializedFile(id: String, path: String, fileType: FileType): StorageFileImpl {
        return StorageFileImpl(
            fileIdOrNull = id,
            pathOrNull = path,
            fileTypeOrNull = fileType,
            createdAtOrNull = 0L,
            modifiedAtOrNull = 0L,
            creatorOrNull = "user",
            ownerNameOrNull = "user",
            sizeOrNull = 0L,
            ownSensitivityLevelOrNull = null
        )
    }

    @Test
    fun `test with no reference fs`() {
        ctx(
            builder = {
                mkdir("home") {
                    touch("a")
                    touch("b")
                }
            },

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(
                        it, "/home", emptyList()
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.file.path == "/home/a" &&
                                it.file.fileId == fsRoot.resolvePath("/home/a").inode()
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.file.path == "/home/b" &&
                                it.file.fileId == fsRoot.resolvePath("/home/b").inode()
                    }

                    assertThatPropertyEquals(diff, { it.diff.size }, 2)

                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test with correct reference fs`() {
        ctx(
            builder = {
                mkdir("home") {
                    touch("a")
                    touch("b")
                    mkdir("c") {
                        touch("1")
                        touch("2")
                        touch("3")
                    }
                }
            },

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(
                        it, "/home", listOf(
                            fsRoot.resolvePath("/home/a").asMaterialized(),
                            fsRoot.resolvePath("/home/b").asMaterialized(),
                            fsRoot.resolvePath("/home/c").asMaterialized()
                        )
                    )

                    assertThatPropertyEquals(diff, { it.diff.size }, 0)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test with no real fs, but a reference`() {
        ctx(
            builder = {
                mkdir("home") {}
            },
            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(
                        it, "/home", listOf(
                            fakeMaterializedFile("1", "/home/foo", FileType.DIRECTORY),
                            fakeMaterializedFile("2", "/home/bar", FileType.FILE)
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated && it.path == "/home/foo"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated && it.path == "/home/bar"
                    }

                    assertThatPropertyEquals(diff, { diff.diff.size }, 2)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test with correct empty reference`() {
        ctx(
            builder = {
                mkdir("home") {}
            },
            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(it, "/home", emptyList())
                    assertThatPropertyEquals(diff, { it.diff.size }, 0)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test reference with duplicate files different ids`() {
        val filePath = "/home/real.txt"
        ctx(
            builder = {
                mkdir("home") {
                    touch("real.txt")
                }
            },

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath(filePath)
                    val referenceFile = realFile.asMaterialized()

                    val diff = indexingService.calculateDiff(
                        it,
                        "/home",
                        listOf(
                            referenceFile,
                            referenceFile.copy(fileIdOrNull = "invalid id")
                        )
                    )

                    assertCollectionHasItem(diff.diff, "Invalidated item") {
                        it is StorageEvent.Invalidated && it.path == filePath
                    }

                    assertCollectionHasItem(diff.diff, "CreatedOrRefreshed item") {
                        it is StorageEvent.CreatedOrRefreshed && it.file.fileId == realFile.inode() && it.file.path == filePath
                    }

                    assertThatPropertyEquals(diff, { it.diff.size }, 2)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test reference root not found`() {
        ctx(
            builder = {},

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(it, "/home/notThere", emptyList())

                    assertCollectionHasItem(diff.diff) { it is StorageEvent.Invalidated && it.path == "/home/notThere" }
                    assertThatPropertyEquals(diff, { it.diff.size }, 1)
                    assertFalse(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test moved file`() {
        ctx(
            builder = {
                mkdir("home") {
                    touch("b")
                }
            },

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath("/home/b")
                    val diff = indexingService.calculateDiff(
                        it, "/home", listOf(
                            realFile.asMaterialized().copy(pathOrNull = "/home/a")
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Moved &&
                                it.file.path == "/home/b" &&
                                it.oldPath == "/home/a" &&
                                it.file.fileId == realFile.inode()
                    }

                    assertThatPropertyEquals(diff, { it.diff.size }, 1)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test moved directory`() {
        ctx(
            builder = {
                mkdir("home") {
                    mkdir("b") {
                        touch("1")
                        touch("2")
                        touch("3")
                    }
                }
            },

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath("/home/b")
                    val diff = indexingService.calculateDiff(
                        it, "/home", listOf(
                            realFile.asMaterialized().copy(pathOrNull = "/home/a")
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Moved &&
                                it.file.path == "/home/b" &&
                                it.oldPath == "/home/a" &&
                                it.file.fileId == realFile.inode()
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated &&
                                it.path == "/home/a"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/b"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/b/1"
                    }


                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/b/2"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/b/3"
                    }

                    assertThatPropertyEquals(diff, { it.diff.size }, 6)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test new sensitivity`() {
        ctx(
            builder = {
                mkdir("home") {
                    touch("a")
                }
            },

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) { ctx ->
                    val realFile = fsRoot.resolvePath("/home/a")
                    val diff = indexingService.calculateDiff(
                        ctx,
                        "/home",
                        listOf(realFile.asMaterialized().copy(ownSensitivityLevelOrNull = SensitivityLevel.SENSITIVE))
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.file.path == "/home/a" &&
                                it.file.ownSensitivityLevel == null &&
                                it.file.fileId == realFile.inode()
                    }

                    assertThatPropertyEquals(diff, { it.diff.size }, 1)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test new directory`() {
        ctx(
            builder = {
                mkdir("home") {
                    touch("a")
                    mkdir("dir") {
                        mkdir("1") {
                            touch("file")
                        }
                    }
                }
            },

            consumer = {
                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val aFile = fsRoot.resolvePath("/home/a")

                    val diff = indexingService.calculateDiff(
                        it, "/home", listOf(aFile.asMaterialized())
                    )

                    assertCollectionHasItem(diff.diff) { it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/dir" }
                    assertCollectionHasItem(diff.diff) { it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/dir/1" }
                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/dir/1/file"
                    }

                    assertThatPropertyEquals(diff, { it.diff.size }, 3)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test on file root`() {
        ctx(
            builder = {
                mkdir("home") {
                    touch("a")
                    mkdir("b") {}
                }
            },

            consumer = {
                val result = runBlocking {
                    indexingService.runDiffOnRoots(
                        mapOf(
                            "/home/a" to emptyList(),
                            "/home/b" to emptyList()
                        )
                    )
                }

                runBlocking { result.second.join() }
                assertFalse(result.first["/home/a"]!!)
                assertTrue(result.first["/home/b"]!!)
                assertThatPropertyEquals(result.first, { it.size }, 2)

                assertEquals(0, EventServiceMock.recordedEvents.size)
            }
        )
    }

    @Test
    fun `test correct emission of events - new directory`() {
        ctx(
            builder = {
                mkdir("home") {
                    touch("a")
                    mkdir("dir") {
                        mkdir("1") {
                            touch("file")
                        }
                    }
                }
            },

            consumer = {
                fun assertCorrectEvents(collection: List<Any>) {
                    assertCollectionHasItem(collection) { it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/dir" }
                    assertCollectionHasItem(collection) { it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/dir/1" }
                    assertCollectionHasItem(collection) {
                        it is StorageEvent.CreatedOrRefreshed && it.file.path == "/home/dir/1/file"
                    }

                    assertThatPropertyEquals(collection, { it.size }, 3)
                }

                runBlocking {
                    commandRunnerFactory.withContext(SERVICE_USER) {
                        val aFile = fsRoot.resolvePath("/home/a")
                        val reference = listOf(aFile.asMaterialized())

                        val diff = indexingService.calculateDiff(
                            it, "/home", reference
                        )

                        assertCorrectEvents(diff.diff)
                        assertTrue(diff.shouldContinue)

                        val (shouldContinue, job) = indexingService.runDiffOnRoots(mapOf("/home" to reference))
                        assertTrue(shouldContinue["/home"]!!)
                        job.join()
                        assertCorrectEvents(EventServiceMock.recordedEvents.map { it.value })
                    }
                }

            }
        )
    }

    @Test
    fun `test emission with FS failure in shouldContinue phase`() {
        val coreFs = mockk<CoreFileSystemService<FSUserContext>>(relaxed = true)
        val commandRunnerFactory = mockk<FSCommandRunnerFactory<FSUserContext>>()
        val eventProducer = mockk<StorageEventProducer>()

        val ctx = mockk<FSUserContext>(relaxed = true)
        coEvery { commandRunnerFactory.invoke(any()) } returns ctx
        coEvery { coreFs.statOrNull(any(), any(), any()) } throws FSException.CriticalException("Mock")

        val indexingService = IndexingService(commandRunnerFactory, coreFs, eventProducer)

        assertFailsWith(FSException.CriticalException::class) {
            runBlocking { indexingService.runDiffOnRoots(mapOf("/" to emptyList())) }
        }

        verify { ctx.close() }
        coVerify(exactly = 0) { eventProducer.produce(any() as StorageEvent) }
    }

    @Test
    fun `test emission with FS failure in diff phase`() {
        val coreFs = mockk<CoreFileSystemService<FSUserContext>>(relaxed = true)
        val commandRunnerFactory = mockk<FSCommandRunnerFactory<FSUserContext>>()
        val eventProducer = mockk<StorageEventProducer>()

        val ctx = mockk<FSUserContext>(relaxed = true)
        coEvery { commandRunnerFactory.invoke(any()) } returns ctx

        val directory = mockk<FileRow>()
        coEvery { directory.fileType } returns FileType.DIRECTORY
        coEvery { directory.isLink } returns false
        coEvery { coreFs.statOrNull(any(), any(), any()) } returns directory

        coEvery { coreFs.listDirectory(any(), any(), any()) } throws FSException.CriticalException("Mock")

        val indexingService = IndexingService(commandRunnerFactory, coreFs, eventProducer)

        val (shouldContinue, job) = runBlocking { indexingService.runDiffOnRoots(mapOf("/" to emptyList())) }
        assertTrue(shouldContinue["/"]!!)
        runBlocking { job.join() }

        verify { ctx.close() }
        coVerify(exactly = 0) { eventProducer.produce(any() as StorageEvent) }
    }
}
