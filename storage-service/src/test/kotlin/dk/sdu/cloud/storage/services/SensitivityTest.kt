package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.ownSensitivityLevel
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.api.sensitivityLevel
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import org.slf4j.Logger
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SensitivityTest {
    val user = "user"

    data class TestContext<Ctx : FSUserContext>(
        val runner: FSCommandRunnerFactory<Ctx>,
        val fs: LowLevelFileSystemInterface<Ctx>,
        val coreFs: CoreFileSystemService<Ctx>,
        val sensitivityService: FileSensitivityService<Ctx>,
        val lookupService: FileLookupService<Ctx>
    )

    private fun initTest(root: String): TestContext<FSUserContext> {
        BackgroundScope.init()

        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        val storageEventProducer = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})
        val sensitivityService =
            FileSensitivityService(fs, storageEventProducer)
        val coreFs = CoreFileSystemService(fs, storageEventProducer)
        val fileLookupService = FileLookupService(coreFs)

        return TestContext(runner, fs, coreFs, sensitivityService, fileLookupService) as TestContext<FSUserContext>
    }

    private fun createRoot(): File = Files.createTempDirectory("sensitivity-test").toFile()

    @Test
    fun `test plain sensitivity`() {
        val root = createRoot()
        with(initTest(root.absolutePath)) {
            root.mkdir("home") {
                mkdir("user") {
                    touch("private")
                    touch("sensitive")
                    touch("confidential")
                }
            }

            runner.withBlockingContext(user) { ctx ->
                sensitivityService.setSensitivityLevel(ctx, "/home/user/private", SensitivityLevel.PRIVATE, null)
                sensitivityService.setSensitivityLevel(ctx, "/home/user/sensitive", SensitivityLevel.SENSITIVE, null)
                sensitivityService.setSensitivityLevel(
                    ctx,
                    "/home/user/confidential",
                    SensitivityLevel.CONFIDENTIAL,
                    null
                )

                val lookup = lookupService.listDirectory(ctx, "/home/user", NormalizedPaginationRequest(null, null))

                val private = lookup.items.find { it.path.fileName() == "private" }!!
                val confidential = lookup.items.find { it.path.fileName() == "confidential" }!!
                val sensitive = lookup.items.find { it.path.fileName() == "sensitive" }!!

                assertEquals(SensitivityLevel.PRIVATE, private.sensitivityLevel)
                assertEquals(SensitivityLevel.CONFIDENTIAL, confidential.sensitivityLevel)
                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)
            }
        }
    }

    @Test
    fun `test no sensitivity`() {
        val root = createRoot()
        with(initTest(root.absolutePath)) {
            root.mkdir("home") {
                mkdir("user") {
                    touch("inherit")
                }
            }

            runner.withBlockingContext(user) { ctx ->
                val lookup = lookupService.listDirectory(ctx, "/home/user", NormalizedPaginationRequest(null, null))
                val inherit = lookup.items.find { it.path.fileName() == "inherit" }!!
                assertEquals(SensitivityLevel.PRIVATE, inherit.sensitivityLevel)
            }
        }
    }

    @Test
    fun `test inheritance`() {
        val root = createRoot()
        with(initTest(root.absolutePath)) {
            root.mkdir("home") {
                mkdir("user") {
                    mkdir("sensitive") {
                        touch("inherit")
                        touch("private")
                    }

                    mkdir("private") {
                        touch("inherit")
                        touch("sensitive")
                    }

                    mkdir("confidential") {
                        touch("sensitive")

                        mkdir("private") {
                            touch("inherit")
                            touch("sensitive")
                        }

                        mkdir("inherit") {
                            mkdir("inherit") {
                                touch("inherit")
                                repeat(10) { touch("$it") }
                            }
                        }
                    }
                }
            }

            runner.withBlockingContext(user) { ctx ->
                sensitivityService.setSensitivityLevel(ctx, "/home/user/sensitive", SensitivityLevel.SENSITIVE)
                sensitivityService.setSensitivityLevel(ctx, "/home/user/sensitive/private", SensitivityLevel.PRIVATE)

                sensitivityService.setSensitivityLevel(ctx, "/home/user/private", SensitivityLevel.PRIVATE)
                sensitivityService.setSensitivityLevel(ctx, "/home/user/private/sensitive", SensitivityLevel.SENSITIVE)

                sensitivityService.setSensitivityLevel(ctx, "/home/user/confidential", SensitivityLevel.CONFIDENTIAL)
                sensitivityService.setSensitivityLevel(
                    ctx,
                    "/home/user/confidential/sensitive",
                    SensitivityLevel.SENSITIVE
                )
                sensitivityService.setSensitivityLevel(ctx, "/home/user/confidential/private", SensitivityLevel.PRIVATE)
                sensitivityService.setSensitivityLevel(
                    ctx,
                    "/home/user/confidential/private/sensitive",
                    SensitivityLevel.SENSITIVE
                )
            }

            fun Page<StorageFile>.find(item: String): StorageFile = items.find { it.path.fileName() == item }!!

            runner.withBlockingContext(user) { ctx ->
                // root folder test
                val lookup = lookupService.listDirectory(ctx, "/home/user", NormalizedPaginationRequest(null, null))
                val sensitive = lookup.find("sensitive")
                val private = lookup.find("private")
                val confidential = lookup.find("confidential")

                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)
                assertEquals(SensitivityLevel.PRIVATE, private.sensitivityLevel)
                assertEquals(SensitivityLevel.CONFIDENTIAL, confidential.sensitivityLevel)

                assertEquals(SensitivityLevel.SENSITIVE, sensitive.ownSensitivityLevel)
                assertEquals(SensitivityLevel.PRIVATE, private.ownSensitivityLevel)
                assertEquals(SensitivityLevel.CONFIDENTIAL, confidential.ownSensitivityLevel)
            }

            runner.withBlockingContext(user) { ctx ->
                // sensitive folder testing
                val lookup =
                    lookupService.listDirectory(ctx, "/home/user/sensitive", NormalizedPaginationRequest(null, null))
                val inherit = lookup.find("inherit")
                val private = lookup.find("private")

                assertEquals(SensitivityLevel.SENSITIVE, inherit.sensitivityLevel)
                assertEquals(SensitivityLevel.PRIVATE, private.sensitivityLevel)

                assertEquals(null, inherit.ownSensitivityLevel)
                assertEquals(SensitivityLevel.PRIVATE, private.sensitivityLevel)
            }

            runner.withBlockingContext(user) { ctx ->
                // private folder testing
                val lookup =
                    lookupService.listDirectory(ctx, "/home/user/private", NormalizedPaginationRequest(null, null))

                val inherit = lookup.find("inherit")
                val sensitive = lookup.find("sensitive")

                assertEquals(SensitivityLevel.PRIVATE, inherit.sensitivityLevel)
                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)

                assertEquals(null, inherit.ownSensitivityLevel)
                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)
            }

            runner.withBlockingContext(user) { ctx ->
                // confidential folder testing
                val lookup =
                    lookupService.listDirectory(ctx, "/home/user/confidential", NormalizedPaginationRequest(null, null))

                val inherit = lookup.find("inherit")
                val sensitive = lookup.find("sensitive")
                val private = lookup.find("private")

                assertEquals(SensitivityLevel.CONFIDENTIAL, inherit.sensitivityLevel)
                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)
                assertEquals(SensitivityLevel.PRIVATE, private.sensitivityLevel)

                assertEquals(null, inherit.ownSensitivityLevel)
                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)
                assertEquals(SensitivityLevel.PRIVATE, private.sensitivityLevel)
            }

            runner.withBlockingContext(user) { ctx ->
                // confidential/private sub folder
                val lookup = lookupService.listDirectory(
                    ctx,
                    "/home/user/confidential/private",
                    NormalizedPaginationRequest(null, null)
                )

                val inherit = lookup.find("inherit")
                val sensitive = lookup.find("sensitive")

                assertEquals(SensitivityLevel.PRIVATE, inherit.sensitivityLevel)
                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)

                assertEquals(null, inherit.ownSensitivityLevel)
                assertEquals(SensitivityLevel.SENSITIVE, sensitive.sensitivityLevel)
            }

            runner.withBlockingContext(user) { ctx ->
                // confidential inheritance test
                val l1 = lookupService.stat(ctx, "/home/user/confidential/inherit")
                val l2 = lookupService.stat(ctx, "/home/user/confidential/inherit/inherit")
                val l3 = lookupService.stat(ctx, "/home/user/confidential/inherit/inherit/inherit")

                assertEquals(SensitivityLevel.CONFIDENTIAL, l1.sensitivityLevel)
                assertEquals(null, l1.ownSensitivityLevel)
                assertEquals(SensitivityLevel.CONFIDENTIAL, l2.sensitivityLevel)
                assertEquals(null, l2.ownSensitivityLevel)
                assertEquals(SensitivityLevel.CONFIDENTIAL, l3.sensitivityLevel)
                assertEquals(null, l3.ownSensitivityLevel)
            }

            runner.withBlockingContext(user) { ctx ->
                // cache test
                lookupService.listDirectory(
                    ctx,
                    "/home/user/confidential/inherit/inherit",
                    NormalizedPaginationRequest(null, null)
                )
            }
        }
    }

    @Test
    fun `test clearing sensitivity`() {
        val root = createRoot()
        with(initTest(root.absolutePath)) {
            root.mkdir("home") {
                mkdir("user") {
                    touch("f")
                }
            }

            runner.withBlockingContext(user) { ctx ->
                sensitivityService.setSensitivityLevel(ctx, "/home/user/f", SensitivityLevel.PRIVATE)
                sensitivityService.clearSensitivityLevel(ctx, "/home/user/f")
            }
        }
    }

    @Test
    fun `test symlinks`() {
        val root = createRoot()
        with(initTest(root.absolutePath)) {
            root.mkdir("home") {
                mkdir("user") {
                    mkdir("confidential") {
                        touch("file")
                    }
                }
            }

            runner.withBlockingContext(user) { ctx ->
                // The expected behavior is that the link has same confidentiality as target (even if this is inherited)
                sensitivityService.setSensitivityLevel(ctx, "/home/user/confidential", SensitivityLevel.CONFIDENTIAL)
                coreFs.createSymbolicLink(ctx, "/home/user/confidential/file", "/home/user/link")
                coreFs.createSymbolicLink(ctx, "/home/user/link", "/home/user/link2")
                coreFs.createSymbolicLink(ctx, "/home/user/link2", "/home/user/link3")

                assertEquals(
                    SensitivityLevel.CONFIDENTIAL,
                    lookupService.stat(ctx, "/home/user/confidential/file").sensitivityLevel
                )

                repeat(10) { log.info("---") }

                assertEquals(
                    SensitivityLevel.CONFIDENTIAL,
                    lookupService.stat(ctx, "/home/user/link").sensitivityLevel
                )

                repeat(10) { log.info("---") }

                assertEquals(
                    SensitivityLevel.CONFIDENTIAL,
                    lookupService.stat(ctx, "/home/user/link2").sensitivityLevel
                )

                repeat(10) { log.info("---") }

                assertEquals(
                    SensitivityLevel.CONFIDENTIAL,
                    lookupService.stat(ctx, "/home/user/link3").sensitivityLevel
                )
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
