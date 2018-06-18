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

        fs.move(fs.openContext("user1"), "home/user1/folder/a", "home/user1/another-one/")

        val existingFolder = File(fsRoot, "home/user1/another-one/a")
        Assert.assertTrue(existingFolder.exists())
    }

    @Test(expected = FSException.CriticalException::class)
    fun testMoveToSameLocation() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val existingFolder = File(fsRoot, "home/user1/folder/a")
        Assert.assertTrue(existingFolder.exists())

        fs.move(fs.openContext("user1"), "home/user1/folder/a", "home/user1/folder/")
    }

    @Test (expected = FSException.CriticalException::class)
    fun testMoveToNonexistingLocation() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val nonexistingFolder = File(fsRoot, "home/user1/folder/newly/created/folder")
        Assert.assertFalse(nonexistingFolder.exists())

        fs.move(fs.openContext("user1"), "home/user1/folder/a", "home/user1/folder/newly/created/folder")

    }

    @Test
    fun testMoveDirectory() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        fs.move(fs.openContext("user1"), "home/user1/folder", "home/user1/another-one")

        val existingFolder = File(fsRoot, "home/user1/another-one/folder/a")
        Assert.assertTrue(existingFolder.exists())
    }
}