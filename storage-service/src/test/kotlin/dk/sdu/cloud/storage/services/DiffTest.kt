package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunner
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import dk.sdu.cloud.storage.util.*
import io.mockk.*
import kotlinx.coroutines.experimental.runBlocking
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
                touch("a")
                touch("b")
            },

            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(
                        it, "/", emptyList()
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.path == "/a" &&
                                it.id == fsRoot.resolvePath("/a").inode()
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.path == "/b" &&
                                it.id == fsRoot.resolvePath("/b").inode()
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
                touch("a")
                touch("b")
                mkdir("c") {
                    touch("1")
                    touch("2")
                    touch("3")
                }
            },

            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(
                        it, "/", listOf(
                            fsRoot.resolvePath("/a").asMaterialized(),
                            fsRoot.resolvePath("/b").asMaterialized(),
                            fsRoot.resolvePath("/c").asMaterialized()
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
            builder = {},
            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(
                        it, "/", listOf(
                            fakeMaterializedFile("1", "/foo", FileType.DIRECTORY),
                            fakeMaterializedFile("2", "/bar", FileType.FILE)
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated && it.id == "1" && it.path == "/foo"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated && it.id == "2" && it.path == "/bar"
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
            builder = {},
            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val diff = indexingService.calculateDiff(it, "/", emptyList())
                    assertThatPropertyEquals(diff, { it.diff.size }, 0)
                    assertTrue(diff.shouldContinue)
                }
            }
        )
    }

    @Test
    fun `test reference with duplicate files different ids`() {
        val filePath = "/real.txt"
        ctx(
            builder = {
                touch("real.txt")
            },

            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath(filePath)
                    val referenceFile = realFile.asMaterialized()

                    val diff = indexingService.calculateDiff(
                        it,
                        "/",
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
                    val diff = indexingService.calculateDiff(it, "/notThere", emptyList())

                    assertCollectionHasItem(diff.diff) { it is StorageEvent.Invalidated && it.path == "/notThere" }
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
                touch("b")
            },

            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath("/b")
                    val diff = indexingService.calculateDiff(
                        it, "/", listOf(
                            realFile.asMaterialized().copy(path = "/a")
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Moved &&
                                it.path == "/b" &&
                                it.oldPath == "/a" &&
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
                mkdir("b") {
                    touch("1")
                    touch("2")
                    touch("3")
                }
            },

            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath("/b")
                    val diff = indexingService.calculateDiff(
                        it, "/", listOf(
                            realFile.asMaterialized().copy(path = "/a")
                        )
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Moved &&
                                it.path == "/b" &&
                                it.oldPath == "/a" &&
                                it.id == realFile.inode()
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.Invalidated &&
                                it.path == "/a"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/b"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/b/1"
                    }


                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/b/2"
                    }

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/b/3"
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
                touch("a")
            },

            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val realFile = fsRoot.resolvePath("/a")
                    val diff = indexingService.calculateDiff(
                        it, "/", listOf(realFile.asMaterialized().copy(sensitivityLevel = SensitivityLevel.SENSITIVE))
                    )

                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed &&
                                it.path == "/a" &&
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
                touch("a")
                mkdir("dir") {
                    mkdir("1") {
                        touch("file")
                    }
                }
            },

            consumer = {
                commandRunnerFactory.withContext(SERVICE_USER) {
                    val aFile = fsRoot.resolvePath("/a")

                    val diff = indexingService.calculateDiff(
                        it, "/", listOf(aFile.asMaterialized())
                    )

                    assertCollectionHasItem(diff.diff) { it is StorageEvent.CreatedOrRefreshed && it.path == "/dir" }
                    assertCollectionHasItem(diff.diff) { it is StorageEvent.CreatedOrRefreshed && it.path == "/dir/1" }
                    assertCollectionHasItem(diff.diff) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/dir/1/file"
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
                touch("a")
                mkdir("b") {}
            },

            consumer = {
                val result = indexingService.runDiffOnRoots(
                    mapOf(
                        "/a" to emptyList(),
                        "/b" to emptyList()
                    )
                )

                runBlocking { result.second.join() }
                assertFalse(result.first["/a"]!!)
                assertTrue(result.first["/b"]!!)
                assertThatPropertyEquals(result.first, { it.size }, 2)

                coVerify(exactly = 0) { mockedEventProducer.emit(any()) }
            }
        )
    }

    @Test
    fun `test correct emission of events - new directory`() {
        ctx(
            builder = {
                touch("a")
                mkdir("dir") {
                    mkdir("1") {
                        touch("file")
                    }
                }
            },

            consumer = {
                fun assertCorrectEvents(collection: List<StorageEvent>) {
                    assertCollectionHasItem(collection) { it is StorageEvent.CreatedOrRefreshed && it.path == "/dir" }
                    assertCollectionHasItem(collection) { it is StorageEvent.CreatedOrRefreshed && it.path == "/dir/1" }
                    assertCollectionHasItem(collection) {
                        it is StorageEvent.CreatedOrRefreshed && it.path == "/dir/1/file"
                    }

                    assertThatPropertyEquals(collection, { it.size }, 3)
                }

                commandRunnerFactory.withContext(SERVICE_USER) {
                    val aFile = fsRoot.resolvePath("/a")
                    val reference = listOf(aFile.asMaterialized())

                    val diff = indexingService.calculateDiff(
                        it, "/", reference
                    )

                    assertCorrectEvents(diff.diff)
                    assertTrue(diff.shouldContinue)

                    val collectedEvents = ArrayList<StorageEvent>()
                    coEvery { mockedEventProducer.emit(capture(collectedEvents)) } just Runs

                    val (shouldContinue, job) = indexingService.runDiffOnRoots(mapOf("/" to reference))
                    assertTrue(shouldContinue["/"]!!)
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

    fun <T> assertCollectionHasItem(
        collection: Iterable<T>,
        description: String = "Custom matcher",
        matcher: (T) -> Boolean
    ) {
        assertTrue("Expected collection to contain an item that matches $description: $collection ") {
            collection.any(matcher)
        }
    }

    fun <T> assertThatInstance(
        instance: T,
        description: String = "Custom matcher",
        matcher: (T) -> Boolean
    ) {
        assertTrue("Expected instance to match $description. Actual value: $instance") { matcher(instance) }
    }

    fun <T, P> assertThatProperty(
        instance: T,
        property: (T) -> P,
        description: String = "Custom matcher",
        matcher: (P) -> Boolean
    ) {
        val prop = property(instance)
        assertTrue(
            "Expected instance's property to match $description." +
                    "\n  Actual value: $instance." +
                    "\n  Computed property: $prop"
        ) {
            matcher(prop)
        }
    }

    fun <T, P> assertThatPropertyEquals(
        instance: T,
        property: (T) -> P,
        value: P,
        description: String = "Custom matcher"
    ) {
        assertThatProperty(instance, property, description) { it == value }
    }
}