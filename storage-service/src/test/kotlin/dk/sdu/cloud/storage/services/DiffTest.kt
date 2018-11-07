package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileChecksum
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.service.test.assertCollectionHasItem
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunner
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.cloudToCephFsDAOWithFixedAnswer
import dk.sdu.cloud.storage.util.createFS
import dk.sdu.cloud.storage.util.inode
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.timestamps
import dk.sdu.cloud.storage.util.touch
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
        val userDao: StorageUserDao,
        val fs: LowLevelFileSystemInterface<Ctx>,
        val mockedEventProducer: StorageEventProducer,
        val coreFs: CoreFileSystemService<Ctx>,
        val indexingService: IndexingService<Ctx>,
        val commandRunnerFactory: FSCommandRunnerFactory<Ctx>
    ) {
        fun File.asMaterialized(): EventMaterializedStorageFile =
            EventMaterializedStorageFile(
                inode(),
                absolutePath.removePrefix(fsRoot.absolutePath).removePrefix("/").let { "/$it" },
                FILE_OWNER,
                if (isDirectory) FileType.DIRECTORY else FileType.FILE,
                timestamps(),
                length(),
                FileChecksum("", ""),
                false,
                null,
                null,
                emptySet(),
                SensitivityLevel.CONFIDENTIAL
            )
    }

    private fun ctx(
        consumer: TestingContext<CephFSCommandRunner>.() -> Unit = {},
        builder: File.() -> Unit
    ): TestingContext<CephFSCommandRunner> {
        val userDao = cloudToCephFsDAOWithFixedAnswer(FILE_OWNER)
        val root = File(createFS(builder))
        val cephFs = CephFileSystem(userDao, root.absolutePath)

        val eventProducer = mockk<StorageEventProducer>(relaxed = true)
        val coreFs = CoreFileSystemService(cephFs, eventProducer)

        val commandRunnerFactory = CephFSCommandRunnerFactory(userDao, true)
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

    private fun fakeMaterializedFile(id: String, path: String, fileType: FileType): EventMaterializedStorageFile {
        return EventMaterializedStorageFile(
            id,
            path,
            FILE_OWNER,
            fileType,
            Timestamps(0L, 0L, 0L),
            0L,
            FileChecksum("", ""),
            false,
            null,
            null,
            emptySet(),
            SensitivityLevel.CONFIDENTIAL
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath(filePath)
                    val referenceFile = realFile.asMaterialized()

                    val diff = indexingService.calculateDiff(
                        it,
                        "/home",
                        listOf(
                            referenceFile,
                            referenceFile.copy(id = "invalid id")
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath("/home/a")
                    val diff = indexingService.calculateDiff(
                        it, "/home", listOf(realFile.asMaterialized().copy(sensitivityLevel = SensitivityLevel.SENSITIVE))
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.path == "/home/a" &&
                                it.sensitivityLevel == SensitivityLevel.CONFIDENTIAL &&
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
                commandRunnerFactory.withContext(SERVICE_USER) {
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
                val result = indexingService.runDiffOnRoots(
                    mapOf(
                        "/home/a" to emptyList(),
                        "/home/b" to emptyList()
                    )
                )

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

                commandRunnerFactory.withContext(SERVICE_USER) {
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
        every { commandRunnerFactory.invoke(any()) } returns ctx
        every { coreFs.statOrNull(any(), any(), any()) } throws FSException.CriticalException("Mock")

        val indexingService = IndexingService(commandRunnerFactory, coreFs, eventProducer)

        assertFailsWith(FSException.CriticalException::class) {
            indexingService.runDiffOnRoots(mapOf("/" to emptyList()))
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
        every { commandRunnerFactory.invoke(any()) } returns ctx

        val directory = mockk<FileRow>()
        every { directory.fileType } returns FileType.DIRECTORY
        every { coreFs.statOrNull(any(), any(), any()) } returns directory

        every { coreFs.listDirectory(any(), any(), any()) } throws FSException.CriticalException("Mock")

        val indexingService = IndexingService(commandRunnerFactory, coreFs, eventProducer)

        val (shouldContinue, job) = indexingService.runDiffOnRoots(mapOf("/" to emptyList()))
        assertTrue(shouldContinue["/"]!!)
        runBlocking { job.join() }

        verify { ctx.close() }
        coVerify(exactly = 0) { eventProducer.emit(any()) }
    }
}
