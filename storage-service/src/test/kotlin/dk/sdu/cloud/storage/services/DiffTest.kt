package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.file.api.StorageFileImpl
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.UIDLookupService
import dk.sdu.cloud.file.services.unixfs.FileAttributeParser
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.unixfs.UnixFileSystem
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.test.assertCollectionHasItem
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.storage.util.createFS
import dk.sdu.cloud.storage.util.inode
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.storageUserDaoWithFixedAnswer
import dk.sdu.cloud.storage.util.timestamps
import dk.sdu.cloud.storage.util.touch
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val FILE_OWNER = "file owner"

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
            StorageFileImpl(
                fileType = if (isDirectory) FileType.DIRECTORY else FileType.FILE,
                fileId = inode(),
                path = absolutePath.removePrefix(fsRoot.absolutePath).removePrefix("/").let { "/$it" },
                ownerName = FILE_OWNER,
                createdAt = timestamps().created,
                modifiedAt = timestamps().modified,
                size = length(),
                creator = "user",
                ownSensitivityLevel = null
            )
    }

    private fun ctx(
        consumer: TestingContext<UnixFSCommandRunner>.() -> Unit = {},
        builder: File.() -> Unit
    ): TestingContext<UnixFSCommandRunner> {
        val userDao = storageUserDaoWithFixedAnswer(FILE_OWNER)
        val root = File(createFS(builder))
        val commandRunnerFactory = UnixFSCommandRunnerFactory(userDao)
        val cephFs = UnixFileSystem(commandRunnerFactory, userDao, FileAttributeParser(userDao), root.absolutePath)

        val eventProducer = mockk<StorageEventProducer>(relaxed = true)
        val coreFs = CoreFileSystemService(cephFs, eventProducer)

        val indexingService = IndexingService(commandRunnerFactory, coreFs, eventProducer)

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
            fileId = id,
            path = path,
            fileType = fileType,
            createdAt = 0L,
            modifiedAt = 0L,
            creator = "user",
            ownerName = "user",
            size = 0L,
            ownSensitivityLevel = null
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
                                it.path == "/home/a" &&
                                it.id == fsRoot.resolvePath("/home/a").inode()
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.path == "/home/b" &&
                                it.id == fsRoot.resolvePath("/home/b").inode()
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
                        it is StorageEvent.Invalidated && it.id == "1" && it.path == "/home/foo"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated && it.id == "2" && it.path == "/home/bar"
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
                            referenceFile.copy(fileId = "invalid id")
                        )
                    )

                    assertCollectionHasItem(diff.diff, "Invalidated item") {
                        it is StorageEvent.Invalidated && it.id == "invalid id" && it.path == filePath &&
                                it.owner == FILE_OWNER
                    }

                    assertCollectionHasItem(diff.diff, "CreatedOrRefreshed item") {
                        it is StorageEvent.CreatedOrRefreshed && it.id == realFile.inode() && it.path == filePath
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
                            realFile.asMaterialized().copy(path = "/home/a")
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Moved &&
                                it.path == "/home/b" &&
                                it.oldPath == "/home/a" &&
                                it.id == realFile.inode()
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
                            realFile.asMaterialized().copy(path = "/home/a")
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Moved &&
                                it.path == "/home/b" &&
                                it.oldPath == "/home/a" &&
                                it.id == realFile.inode()
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated &&
                                it.path == "/home/a"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/home/b"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/home/b/1"
                    }


                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/home/b/2"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/home/b/3"
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
                        listOf(realFile.asMaterialized().copy(sensitivityLevel = SensitivityLevel.SENSITIVE))
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.path == "/home/a" &&
                                it.sensitivityLevel == SensitivityLevel.PRIVATE &&
                                it.id == realFile.inode()
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

                    assertCollectionHasItem(diff.diff) { it is StorageEvent.CreatedOrRefreshed && it.path == "/home/dir" }
                    assertCollectionHasItem(diff.diff) { it is StorageEvent.CreatedOrRefreshed && it.path == "/home/dir/1" }
                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/home/dir/1/file"
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

                coVerify(exactly = 0) { mockedEventProducer.emit(any()) }
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
                fun assertCorrectEvents(collection: List<StorageEvent>) {
                    assertCollectionHasItem(collection) { it is StorageEvent.CreatedOrRefreshed && it.path == "/home/dir" }
                    assertCollectionHasItem(collection) { it is StorageEvent.CreatedOrRefreshed && it.path == "/home/dir/1" }
                    assertCollectionHasItem(collection) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/home/dir/1/file"
                    }

                    assertThatPropertyEquals(collection, { it.size }, 3)
                }

                commandRunnerFactory.withBlockingContext(SERVICE_USER) {
                    val aFile = fsRoot.resolvePath("/home/a")
                    val reference = listOf(aFile.asMaterialized())

                    val diff = indexingService.calculateDiff(
                        it, "/home", reference
                    )

                    assertCorrectEvents(diff.diff)
                    assertTrue(diff.shouldContinue)

                    val collectedEvents = ArrayList<StorageEvent>()
                    coEvery { mockedEventProducer.emit(capture(collectedEvents)) } just Runs

                    val (shouldContinue, job) = indexingService.runDiffOnRoots(mapOf("/home" to reference))
                    assertTrue(shouldContinue["/home"]!!)
                    runBlocking { job.join() }
                    assertCorrectEvents(collectedEvents)
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
        coVerify(exactly = 0) { eventProducer.emit(any()) }
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
        coVerify(exactly = 0) { eventProducer.emit(any()) }
    }
}
