package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.KnowledgeMode
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.service.Loggable

/**
 * Service responsible for handling operations related to indexing
 */
class IndexingService<Ctx : FSUserContext>(private val aclService: AclService) {
    suspend fun verifyKnowledge(
        ctx: Ctx,
        files: List<String>,
        mode: KnowledgeMode = KnowledgeMode.List()
    ): List<Boolean> {
        return when (mode) {
            is KnowledgeMode.List -> {
                val parents = files.asSequence().map { it.parent() }.toSet()
                //val knowledgeByParent = parents.map { it to fs.checkPermissions(ctx, it, requireWrite = false) }.toMap()
                val knowledgeByParent =
                    parents.map { it to aclService.hasPermission(it, ctx.user, AccessRight.READ) }.toMap()

                files.map { knowledgeByParent[it.parent()]!! }
            }

            is KnowledgeMode.Permission -> {
                files.map {
                    aclService.hasPermission(
                        it,
                        ctx.user,
                        if (mode.requireWrite) AccessRight.WRITE else AccessRight.READ
                    )
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
