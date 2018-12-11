package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FavoriteService
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.createDummyFS
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File

class FavoriteTest {
    private fun createService(
        root: String,
        emitter: StorageEventProducer = mockk(relaxed = true)
    ): Pair<UnixFSCommandRunnerFactory, FavoriteService<UnixFSCommandRunner>> {
        val (runner, fs) = unixFSWithRelaxedMocks(root)
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
        runner.withBlockingContext("user1") { service.markAsFavorite(it, fileToFavorite) }
        Assert.assertTrue(File(fsRoot, favoriteLink).exists())

        Thread.sleep(1000)

        coVerify {
            emitter.emit(match { it is StorageEvent.CreatedOrRefreshed && it.path == "/$favoriteLink" })
        }
    }

    @Test
    fun testRemoveFavorites() {
        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath)

        val favoriteLink = "home/user1/Favorites/a"
        val fileToFavorite = "home/user1/folder/a"
        runner.withBlockingContext("user1") { ctx ->
            service.markAsFavorite(ctx, "/$fileToFavorite")

            Assert.assertTrue(File(fsRoot, favoriteLink).exists())
            service.removeFavorite(ctx, "/$favoriteLink")
            Assert.assertFalse(File(fsRoot, favoriteLink).exists())
        }
    }
}
