package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunner
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import io.mockk.*
import org.junit.Assert
import org.junit.Test
import java.io.File

class FavoriteTest {
    private fun createService(
        root: String,
        emitter: StorageEventProducer = mockk(relaxed = true)
    ): Pair<CephFSCommandRunnerFactory, FavoriteService<CephFSCommandRunner>> {
        val (runner, fs) = cephFSWithRelaxedMocks(root)
        val coreFs = CoreFileSystemService(fs, emitter)
        return Pair(runner, FavoriteService(coreFs))
    }

    @Test
    fun testCreateFavorites() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } just Runs

        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath, emitter)

        val favoriteLink = "home/user1/Favorites/a"
        val fileToFavorite = "home/user1/folder/a"

        Assert.assertFalse(File(fsRoot, favoriteLink).exists())
        runner.withContext("user1") { service.markAsFavorite(it, fileToFavorite) }
        Assert.assertTrue(File(fsRoot, favoriteLink).exists())

        Thread.sleep(1000)

        coVerify {
            emitter.emit(match { it is StorageEvent.CreatedOrModified && it.path == "/$favoriteLink" })
        }
    }

    @Test
    fun testRemoveFavorites() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val favoriteLink = "home/user1/Favorites/a"
        val fileToFavorite = "home/user1/folder/a"
        runner.withContext("user1") { ctx ->
            service.markAsFavorite(ctx, "/$fileToFavorite")

            Assert.assertTrue(File(fsRoot, favoriteLink).exists())
            service.removeFavorite(ctx, "/$favoriteLink")
            Assert.assertFalse(File(fsRoot, favoriteLink).exists())
        }
    }
}