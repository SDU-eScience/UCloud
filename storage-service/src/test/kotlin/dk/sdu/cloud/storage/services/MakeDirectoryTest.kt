package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunner
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.util.FSException
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File

class MakeDirectoryTest {
    private fun createService(root: String): Pair<CephFSCommandRunnerFactory, CoreFileSystemService<CephFSCommandRunner>> {
        val (runner, fs) = cephFSWithRelaxedMocks(root)
        return Pair(runner, CoreFileSystemService(fs, mockk(relaxed = true)))
    }

    @Test(expected = FSException.AlreadyExists::class)
    fun testNewDirAlreadyExists() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val existingFolder = File(fsRoot, "home/user1/folder")
        Assert.assertTrue(existingFolder.exists())

        runner.withContext("user1") {
            service.makeDirectory(it, "/home/user1/folder")
        }
    }

    @Test
    fun testPathSanitation() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val existingFolder = File(fsRoot, "home/user1/folder/Weirdpath")
        Assert.assertFalse(existingFolder.exists())

        runner.withContext("user1") {
            service.makeDirectory(it, "/home/user1/folder/./../folder/./Weirdpath")
        }

        val existingFolder2 = File(fsRoot, "home/user1/folder/Weirdpath")
        Assert.assertTrue(existingFolder2.exists())
    }
}