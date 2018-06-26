package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.util.FSUserContext
import dk.sdu.cloud.storage.util.favoritesDirectory
import dk.sdu.cloud.storage.util.fileName
import dk.sdu.cloud.storage.util.joinPath

class FavoriteService(val fs: CoreFileSystemService) {
     fun markAsFavorite(ctx: FSUserContext, fileToFavorite: String) {
        val favoritesDirectory = favoritesDirectory(ctx)
        if (!fs.exists(ctx, favoritesDirectory)) {
            fs.makeDirectory(ctx, favoritesDirectory)
        }

        fs.createSymbolicLink(ctx, fileToFavorite, joinPath(favoritesDirectory, fileToFavorite.fileName()))
    }

    fun removeFavorite(ctx: FSUserContext, favoriteFileToRemove: String) {
        val stat = fs.stat(ctx, favoriteFileToRemove, setOf(FileAttribute.INODE))
        val allFavorites = retrieveFavorites(ctx)
        val toRemove = allFavorites.filter { it.inode == stat.inode || it.favInode == stat.inode }
        if (toRemove.isEmpty()) return
        toRemove.forEach { fs.delete(ctx, it.from) }
    }

    fun retrieveFavoriteInodeSet(ctx: FSUserContext): Set<String> =
        retrieveFavorites(ctx).map { it.inode }.toSet()

    fun retrieveFavorites(ctx: FSUserContext): List<FavoritedFile> {
        return fs.listDirectory(
            ctx, favoritesDirectory(ctx), setOf(
                FileAttribute.FILE_TYPE,
                FileAttribute.PATH,
                FileAttribute.LINK_TARGET,
                FileAttribute.LINK_INODE,
                FileAttribute.INODE
            )
        ).map {
            FavoritedFile(
                type = it.fileType,
                from = it.path,
                to = it.linkTarget,
                inode = it.linkInode,
                favInode = it.inode
            )
        }
    }
}