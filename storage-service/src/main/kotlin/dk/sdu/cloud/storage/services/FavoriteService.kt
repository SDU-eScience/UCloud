package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.util.favoritesDirectory
import dk.sdu.cloud.storage.util.fileName
import dk.sdu.cloud.storage.util.joinPath

class FavoriteService<Ctx : FSUserContext>(val fs: CoreFileSystemService<Ctx>) {
     fun markAsFavorite(ctx: Ctx, fileToFavorite: String) {
        val favoritesDirectory = favoritesDirectory(ctx)
        if (!fs.exists(ctx, favoritesDirectory)) {
            fs.makeDirectory(ctx, favoritesDirectory)
        }

        fs.createSymbolicLink(ctx, fileToFavorite, joinPath(favoritesDirectory, fileToFavorite.fileName()))
    }

    fun removeFavorite(ctx: Ctx, favoriteFileToRemove: String) {
        val stat = fs.stat(ctx, favoriteFileToRemove, setOf(FileAttribute.INODE))
        val allFavorites = retrieveFavorites(ctx)
        val toRemove = allFavorites.filter { it.inode == stat.inode || it.favInode == stat.inode }
        if (toRemove.isEmpty()) return
        toRemove.forEach { fs.delete(ctx, it.from) }
    }

    fun retrieveFavoriteInodeSet(ctx: Ctx): Set<String> =
        retrieveFavorites(ctx).asSequence().map { it.inode }.toSet()

    fun retrieveFavorites(ctx: Ctx): List<FavoritedFile> {
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