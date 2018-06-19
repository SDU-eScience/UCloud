package dk.sdu.cloud.storage.services

import org.junit.Assert
import org.junit.Test
import java.io.File

class MoveTest {

    @Test
    fun testMove() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val nonExistingFolder = File(fsRoot, "home/user1/another-one/a")
        Assert.assertFalse(nonExistingFolder.exists())

        fs.withContext("user1") {
            fs.move(it, "home/user1/folder/a", "home/user1/another-one/a")
        }

        val existingFolder = File(fsRoot, "home/user1/another-one/a")
        Assert.assertTrue(existingFolder.exists())
    }

    @Test(expected = FSException.AlreadyExists::class)
    fun testMoveToSameLocation() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val existingFolder = File(fsRoot, "home/user1/folder/a")
        Assert.assertTrue(existingFolder.exists())

        fs.withContext("user1") {
            fs.move(it, "home/user1/folder/a", "home/user1/folder/")
        }
    }

    @Test (expected = FSException.NotFound::class)
    fun testMoveToNonexistingLocation() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val nonexistingFolder = File(fsRoot, "home/user1/folder/newly/created/folder")
        Assert.assertFalse(nonexistingFolder.exists())

        fs.withContext("user1") {
            fs.move(it, "home/user1/folder/a", "home/user1/folder/newly/created/folder")
        }

    }

    @Test
    fun testMoveDirectory() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        fs.withContext("user1") {
            fs.move(it, "/home/user1/folder", "/home/user1/new-folder")
        }

        val existingFolder = File(fsRoot, "home/user1/new-folder/a")
        Assert.assertTrue(existingFolder.exists())
    }
}