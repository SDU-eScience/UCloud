package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.api.StorageEventProducer
import io.mockk.*
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FavoriteTest {

    @Test
    fun testCreateFavorites() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } just Runs

        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath,
            eventProducer = emitter
        )

        val favoriteLink = "home/user1/Favorites/a"
        val fileToFavorite = "home/user1/folder/a"

        Assert.assertFalse(File(fsRoot, favoriteLink).exists())
        fs.createFavorite(fs.openContext("user1"), fileToFavorite)
        Assert.assertTrue(File(fsRoot, favoriteLink).exists())

        Thread.sleep(1000)

        coVerify {
            emitter.emit(match { it is StorageEvent.CreatedOrModified && it.path == "/$favoriteLink" })
        }
    }

    @Test
    fun testRemoveFavorites() {
        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath
        )

        val favoriteLink = "home/user1/Favorites/a"
        val fileToFavorite = "home/user1/folder/a"
        fs.createFavorite(fs.openContext("user1"), "/$fileToFavorite")

        Assert.assertTrue(File(fsRoot, favoriteLink).exists())
        fs.removeFavorite(fs.openContext("user1"), "/$favoriteLink")
        Assert.assertFalse(File(fsRoot, favoriteLink).exists())

    }
}