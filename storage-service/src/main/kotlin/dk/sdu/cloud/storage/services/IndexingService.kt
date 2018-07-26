package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.parent

/**
 * Service responsible for handling operations related to indexing
 */
class IndexingService<Ctx : FSUserContext>(
    private val fs: CoreFileSystemService<Ctx>
) {
    fun verifyKnowledge(ctx: Ctx, files: List<String>): List<Boolean> {
        val parents = files.map { it.parent() }.toSet()
        val knowledgeByParent = parents.map { it to hasReadInDirectory(ctx, it) }.toMap()
        return files.map { knowledgeByParent[it.parent()]!! }
    }

    private fun hasReadInDirectory(ctx: Ctx, directoryPath: String): Boolean {
        return try {
            // TODO We don't actually have to list anything in the directory. Would be faster without
            fs.listDirectory(ctx, directoryPath, setOf(FileAttribute.INODE))
            true
        } catch (ex: FSException) {
            when (ex) {
                is FSException.PermissionException, is FSException.NotFound -> false
                else -> throw ex
            }
        }
    }
}