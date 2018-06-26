package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.FileType

data class FavoritedFile(val type: FileType, val from: String, val to: String, val inode: String, val favInode: String)