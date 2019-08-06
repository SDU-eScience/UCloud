package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.file.util.createDummyFS
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.file.util.mkdir
import dk.sdu.cloud.file.util.touch
import io.mockk.mockk
import org.junit.Assert
import java.io.File
import kotlin.test.Test

class MoveTest {
    private fun createService(root: String): Pair<LinuxFSRunnerFactory, CoreFileSystemService<LinuxFSRunner>> {
        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        val fileSensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        return Pair(runner,
            CoreFileSystemService(fs, mockk(relaxed = true), fileSensitivityService, ClientMock.authenticatedClient)
        )
    }

    private data class TestContext(
        val fsRoot: File,
        val runner: LinuxFSRunnerFactory,
        val service: CoreFileSystemService<LinuxFSRunner>,
        val ctx: LinuxFSRunner
    )

    private fun runTest(user: String = "user1", consumer: suspend TestContext.() -> Unit) {
        BackgroundScope.reset()
        try {
            val fsRoot = createDummyFS()
            val (runner, service) = createService(fsRoot.absolutePath)

            runner.withBlockingContext(user) {
                val testContext = TestContext(fsRoot, runner, service, it)
                testContext.consumer()
            }
        } finally {
            BackgroundScope.stop()
        }
    }

    @Test
    fun `test simple move`() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val target = "/home/user1/another-one/a"
        val nonExistingFolder = File(fsRoot, "./$target")
        Assert.assertFalse(nonExistingFolder.exists())

        runner.withBlockingContext("user1") {
            service.move(it, "/home/user1/folder/a", target, WriteConflictPolicy.OVERWRITE)
        }

        val existingFolder = File(fsRoot, "./$target")
        Assert.assertTrue(existingFolder.exists())
    }

    @Test(expected = FSException.AlreadyExists::class)
    fun `test move to same location`() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val child = "/home/user1/folder/a"
        val existingFolder = File(fsRoot, "./$child")
        Assert.assertTrue(existingFolder.exists())

        runner.withBlockingContext("user1") {
            service.move(it, child, "/home/user1/folder/", WriteConflictPolicy.REJECT)
        }
    }

    @Test(expected = FSException.NotFound::class)
    fun `test moving to non existing location`() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val child = "/home/user1/folder/newly/created/folder"
        val nonexistingFolder = File(fsRoot, "./$child")
        Assert.assertFalse(nonexistingFolder.exists())

        runner.withBlockingContext("user1") {
            service.move(
                it,
                "/home/user1/folder/a",
                child,
                WriteConflictPolicy.OVERWRITE
            )
        }

    }

    @Test
    fun `test moving a directory`() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        runner.withBlockingContext("user1") {
            service.move(it, "/home/user1/folder", "/home/user1/new-folder", WriteConflictPolicy.OVERWRITE)
        }

        val existingFolder = File(fsRoot, "home/user1/new-folder/a")
        Assert.assertTrue(existingFolder.exists())
    }

    @Test(expected = FSException.BadRequest::class)
    fun `test moving directory to a file with overwrite flag`() {
        runTest {
            val userRootPath = "/home/user1"
            val userRoot = fsRoot.resolve(".$userRootPath")
            val conflictFileName = "conflict"
            val wrapper = "wrapper"

            userRoot.apply {
                mkdir(conflictFileName) {}

                mkdir(wrapper) {
                    touch(conflictFileName)
                }
            }

            service.move(
                ctx,
                joinPath(userRootPath, conflictFileName),
                joinPath(userRootPath, wrapper, conflictFileName),
                WriteConflictPolicy.OVERWRITE
            )
        }
    }

    @Test
    fun `test moving a non empty directory into an empty directory`() {
        runTest {
            val userRootPath = "/home/user1"
            val conflictFileName = "conflict"
            val wrapper = "wrapper"
            val source: File
            val destination: File

            fsRoot.resolve(".$userRootPath").apply {
                source = mkdir(conflictFileName) {
                    touch("a")
                }

                mkdir(wrapper) {
                    destination = mkdir(conflictFileName) {}
                }
            }

            service.move(
                ctx,
                joinPath(userRootPath, conflictFileName),
                joinPath(userRootPath, wrapper, conflictFileName),
                WriteConflictPolicy.OVERWRITE
            )

            assertThatPropertyEquals(destination.list(), { it.size }, 1)
            assertThatPropertyEquals(source, { it.exists() }, false)
        }
    }

    @Test(expected = FSException.BadRequest::class)
    fun `test moving empty directory into a non-empty directory`() {
        runTest {
            val userRootPath = "/home/user1"
            val conflictFileName = "conflict"
            val wrapper = "wrapper"

            fsRoot.resolve(".$userRootPath").apply {
                mkdir(conflictFileName) {}

                mkdir(wrapper) {
                    mkdir(conflictFileName) {
                        touch("a")
                    }
                }
            }

            service.move(
                ctx,
                joinPath(userRootPath, conflictFileName),
                joinPath(userRootPath, wrapper, conflictFileName),
                WriteConflictPolicy.OVERWRITE
            )
        }
    }

    @Test(expected = FSException.BadRequest::class)
    fun `test moving non-empty directory into a non-empty directory`() {
        runTest {
            val userRootPath = "/home/user1"
            val conflictFileName = "conflict"
            val wrapper = "wrapper"

            fsRoot.resolve(".$userRootPath").apply {
                mkdir(conflictFileName) {
                    touch("a")
                }

                mkdir(wrapper) {
                    mkdir(conflictFileName) {
                        touch("b")
                    }
                }
            }

            service.move(
                ctx,
                joinPath(userRootPath, conflictFileName),
                joinPath(userRootPath, wrapper, conflictFileName),
                WriteConflictPolicy.OVERWRITE
            )
        }
    }
}
