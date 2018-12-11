package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.createDummyFS
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File

class MoveTest {
    private fun createService(root: String): Pair<UnixFSCommandRunnerFactory, CoreFileSystemService<UnixFSCommandRunner>> {
        val (runner, fs) = unixFSWithRelaxedMocks(root)
        return Pair(runner, CoreFileSystemService(fs, mockk(relaxed = true)))
    }

    @Test
    fun testMove() {
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
    fun testMoveToSameLocation() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val existingFolder = File(fsRoot, "home/user1/folder/a")
        Assert.assertTrue(existingFolder.exists())

        runner.withBlockingContext("user1") {
            service.move(it, "home/user1/folder/a", "home/user1/folder/", WriteConflictPolicy.REJECT)
        }
    }

    @Test(expected = FSException.NotFound::class)
    fun testMoveToNonexistingLocation() {
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
    fun testMoveDirectory() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        runner.withBlockingContext("user1") {
            service.move(it, "/home/user1/folder", "/home/user1/new-folder", WriteConflictPolicy.OVERWRITE)
        }

        val existingFolder = File(fsRoot, "home/user1/new-folder/a")
        Assert.assertTrue(existingFolder.exists())
    }
}
