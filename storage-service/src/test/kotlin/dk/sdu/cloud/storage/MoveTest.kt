package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.services.FileSystemException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MoveTest {
    fun createFileSystem(): File {
        val fsRoot = Files.createTempDirectory("share-service-test").toFile()
        fsRoot.apply {
            mkdir("home") {
                mkdir("user1") {
                    mkdir("folder") {
                        touch("a", "File A")
                        touch("b", "File B")
                        touch("c", "File C")
                    }

                    mkdir("another-one") {
                        touch("file")
                    }
                }
            }
        }
        return fsRoot
    }

    @Test
    fun testMove() {
        val fsRoot = createFileSystem()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val nonExistingFolder = File(fsRoot, "home/user1/another-one/a")
        Assert.assertFalse(nonExistingFolder.exists())

        fs.move(fs.openContext("user1"), "home/user1/folder/a", "home/user1/another-one/")

        val existingFolder = File(fsRoot, "home/user1/another-one/a")
        Assert.assertTrue(existingFolder.exists())
    }

    @Test(expected = FileSystemException.CriticalException::class)
    fun testMoveToSameLocation() {
        val fsRoot = createFileSystem()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val existingFolder = File(fsRoot, "home/user1/folder/a")
        Assert.assertTrue(existingFolder.exists())

        fs.move(fs.openContext("user1"), "home/user1/folder/a", "home/user1/folder/")
    }

    @Test (expected = FileSystemException.CriticalException::class)
    fun testMoveToNonexistingLocation() {
        val fsRoot = createFileSystem()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val nonexistingFolder = File(fsRoot, "home/user1/folder/newly/created/folder")
        Assert.assertFalse(nonexistingFolder.exists())

        fs.move(fs.openContext("user1"), "home/user1/folder/a", "home/user1/folder/newly/created/folder")

    }

    @Test
    fun testMoveDirectory() {
        val fsRoot = createFileSystem()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        fs.move(fs.openContext("user1"), "home/user1/folder", "home/user1/another-one")

        val existingFolder = File(fsRoot, "home/user1/another-one/folder/a")
        Assert.assertTrue(existingFolder.exists())
    }
}