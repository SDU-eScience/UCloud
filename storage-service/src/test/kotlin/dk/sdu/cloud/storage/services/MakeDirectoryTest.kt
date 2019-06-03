package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.storage.util.createDummyFS
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File

class MakeDirectoryTest {
    private fun createService(root: String): Pair<LinuxFSRunnerFactory, CoreFileSystemService<LinuxFSRunner>> {
        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        val fileSensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        return Pair(runner,
            CoreFileSystemService(fs, mockk(relaxed = true), fileSensitivityService, ClientMock.authenticatedClient)
        )
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
