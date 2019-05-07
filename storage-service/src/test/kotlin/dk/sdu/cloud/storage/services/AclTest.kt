package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AclTest {
    data class TestContext<Ctx : FSUserContext>(
        val runner: FSCommandRunnerFactory<Ctx>,
        val fs: LowLevelFileSystemInterface<Ctx>,
        val coreFs: CoreFileSystemService<Ctx>,
        val sensitivityService: FileSensitivityService<Ctx>,
        val lookupService: FileLookupService<Ctx>
    )

    private fun initTest(root: String): TestContext<FSUserContext> {
        BackgroundScope.init()

        val uidLookupService = object : UIDLookupService {
            override suspend fun lookup(username: String): Long? {
                return when (username) {
                    "A" -> 1001
                    "B" -> 1002
                    else -> null
                }
            }

            override suspend fun reverseLookup(uid: Long): String? {
                return when (uid) {
                    1001L -> "A"
                    1002L -> "B"
                    else -> null
                }
            }

            override fun storeMapping(username: String, cloudUid: Long) {

            }
        }

        val (runner, fs) = linuxFSWithRelaxedMocks(root, uidLookupService)
        val storageEventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})
        val sensitivityService =
            FileSensitivityService(fs, storageEventProducer)
        val coreFs = CoreFileSystemService(fs, storageEventProducer)
        val fileLookupService = FileLookupService(coreFs)

        return TestContext(runner, fs, coreFs, sensitivityService, fileLookupService) as TestContext<FSUserContext>
    }

    private fun createRoot(): File = Files.createTempDirectory("sensitivity-test").toFile()
    @Ignore
    @Test
    fun `testing low-level interface recursive`() {
        val root = createRoot().apply {
            mkdir("home") {
                mkdir("A") {
                    mkdir("share") {
                        touch("1")
                    }
                }

                mkdir("B") {

                }
            }
        }

        with(initTest(root.absolutePath)) {
            runner.withBlockingContext("A") {
                fs.createACLEntry(it, "/home/A/share", FSACLEntity.User("B"), setOf(AccessRight.READ), recursive = true)

                fs.createACLEntry(
                    it,
                    "/home/A/share",
                    FSACLEntity.User("B"),
                    setOf(AccessRight.READ),
                    defaultList = true,
                    recursive = true
                )
            }
        }
    }
    @Ignore
    @Test
    fun `testing low-level interface on file`() {
        val root = createRoot().apply {
            mkdir("home") {
                mkdir("A") {
                    mkdir("share") {
                        touch("1")
                    }
                }

                mkdir("B") {

                }
            }
        }

        with(initTest(root.absolutePath)) {
            runner.withBlockingContext("A") {
                fs.createACLEntry(it, "/home/A/share/1", FSACLEntity.User("B"), setOf(AccessRight.READ))

                fs.createACLEntry(
                    it,
                    "/home/A/share/1",
                    FSACLEntity.User("B"),
                    setOf(AccessRight.READ),
                    defaultList = true
                )
            }
        }
    }
    @Ignore
    @Test
    fun `testing low-level interface on directory`() {
        val root = createRoot().apply {
            mkdir("home") {
                mkdir("A") {
                    mkdir("share") {
                        touch("1")
                    }
                }

                mkdir("B") {

                }
            }
        }

        with(initTest(root.absolutePath)) {
            runner.withBlockingContext("A") {
                fs.createACLEntry(it, "/home/A/share", FSACLEntity.User("B"), setOf(AccessRight.READ))

                fs.createACLEntry(
                    it,
                    "/home/A/share",
                    FSACLEntity.User("B"),
                    setOf(AccessRight.READ),
                    defaultList = true
                )
            }
        }
    }
}
