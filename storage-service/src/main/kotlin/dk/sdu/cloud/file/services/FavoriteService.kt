package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.util.favoritesDirectory

class FavoriteService<Ctx : FSUserContext>(val fs: CoreFileSystemService<Ctx>) {
    suspend fun markAsFavorite(ctx: Ctx, fileToFavorite: String) {
        val favoritesDirectory = favoritesDirectory(ctx)
        if (!fs.exists(ctx, favoritesDirectory)) {
            fs.makeDirectory(ctx, favoritesDirectory)
        }

        fs.createSymbolicLink(ctx, fileToFavorite, joinPath(favoritesDirectory, fileToFavorite.fileName()))
    }

    suspend fun removeFavorite(ctx: Ctx, favoriteFileToRemove: String) {
        val stat = fs.stat(ctx, favoriteFileToRemove, setOf(FileAttribute.INODE))
        removeFavoriteViaId(ctx, stat.inode)
    }

    suspend fun removeFavoriteViaId(ctx: Ctx, fileId: String) {
        val allFavorites = retrieveFavorites(ctx)
        val toRemove = allFavorites.filter { it.inode == fileId || it.favInode == fileId }
        if (toRemove.isEmpty()) return
        toRemove.forEach { fs.delete(ctx, it.from) }
    }

    suspend fun retrieveFavoriteInodeSet(ctx: Ctx): Set<String> =
        retrieveFavorites(ctx).asSequence().map { it.inode }.toSet()

    suspend fun retrieveFavorites(ctx: Ctx): List<FavoritedFile> {
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
