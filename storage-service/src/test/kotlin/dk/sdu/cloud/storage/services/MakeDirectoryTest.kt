package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.storage.util.createDummyFS
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File

class MakeDirectoryTest {
    private fun createService(root: String): Pair<UnixFSCommandRunnerFactory, CoreFileSystemService<UnixFSCommandRunner>> {
        val (runner, fs) = unixFSWithRelaxedMocks(root)
        return Pair(runner, CoreFileSystemService(fs, mockk(relaxed = true)))
    }

    @Test(expected = FSException.AlreadyExists::class)
    fun testNewDirAlreadyExists() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val existingFolder = File(fsRoot, "home/user1/folder")
        Assert.assertTrue(existingFolder.exists())

        runner.withBlockingContext("user1") {
            service.makeDirectory(it, "/home/user1/folder")
        }
    }

    @Test
    fun testPathSanitation() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val existingFolder = File(fsRoot, "home/user1/folder/Weirdpath")
        Assert.assertFalse(existingFolder.exists())

        runner.withBlockingContext("user1") {
            service.makeDirectory(it, "/home/user1/folder/./../folder/./Weirdpath")
        }

        val existingFolder2 = File(fsRoot, "home/user1/folder/Weirdpath")
        Assert.assertTrue(existingFolder2.exists())
    }
}
