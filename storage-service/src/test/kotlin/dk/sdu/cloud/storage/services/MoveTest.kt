package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.storage.util.createDummyFS
import dk.sdu.cloud.storage.util.mkdir
import dk.sdu.cloud.storage.util.touch
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import io.mockk.mockk
import org.junit.Assert
import java.io.File
import kotlin.test.Test

class MoveTest {
    private fun createService(root: String): Pair<UnixFSCommandRunnerFactory, CoreFileSystemService<UnixFSCommandRunner>> {
        val (runner, fs) = unixFSWithRelaxedMocks(root)
        return Pair(runner, CoreFileSystemService(fs, mockk(relaxed = true)))
    }

    private data class TestContext(
        val fsRoot: File,
        val runner: UnixFSCommandRunnerFactory,
        val service: CoreFileSystemService<UnixFSCommandRunner>,
        val ctx: UnixFSCommandRunner
    )

    private fun runTest(user: String = "user1", consumer: suspend TestContext.() -> Unit) {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        runner.withBlockingContext(user) {
            val testContext = TestContext(fsRoot, runner, service, it)
            testContext.consumer()
        }
    }

    @Test
    fun `test simple move`() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val nonExistingFolder = File(fsRoot, "home/user1/another-one/a")
        Assert.assertFalse(nonExistingFolder.exists())

        runner.withBlockingContext("user1") {
            service.move(it, "home/user1/folder/a", "home/user1/another-one/a", WriteConflictPolicy.OVERWRITE)
        }

        val existingFolder = File(fsRoot, "home/user1/another-one/a")
        Assert.assertTrue(existingFolder.exists())
    }

    @Test(expected = FSException.AlreadyExists::class)
    fun `test move to same location`() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val existingFolder = File(fsRoot, "home/user1/folder/a")
        Assert.assertTrue(existingFolder.exists())

        runner.withBlockingContext("user1") {
            service.move(it, "home/user1/folder/a", "home/user1/folder/", WriteConflictPolicy.REJECT)
        }
    }

    @Test(expected = FSException.NotFound::class)
    fun `test moving to non existing location`() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val nonexistingFolder = File(fsRoot, "home/user1/folder/newly/created/folder")
        Assert.assertFalse(nonexistingFolder.exists())

        runner.withBlockingContext("user1") {
            service.move(
                it,
                "home/user1/folder/a",
                "home/user1/folder/newly/created/folder",
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
}
