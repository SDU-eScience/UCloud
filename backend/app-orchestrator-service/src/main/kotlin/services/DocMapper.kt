package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.ResourceDocument
import dk.sdu.cloud.app.orchestrator.api.ComputeSupport
import dk.sdu.cloud.app.orchestrator.api.Jobs
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.json.JsonElement
import java.util.HashMap
import java.util.HashSet

class DocMapper<A, B>(
    private val idCards: IIdCardService,
    private val productCache: ProductCache,
    private val providers: ProviderCommunications,
    private val converter: suspend DocMapperInfo<A>.() -> B,
) {
    suspend fun map(card: IdCard?, doc: ResourceDocument<A>): B {
        val resolvedProduct = productCache.productIdToProduct(doc.product) ?: error("unknown product: ${doc.product}")
        return DocMapperInfo<A>(
            doc,
            doc.id.toString(),
            ResourceOwner(
                idCards.lookupUid(doc.createdBy) ?: "_ucloud",
                idCards.lookupPid(doc.project),
            ),
            doc.update.asSequence().filterNotNull().map {
                GenericResourceUpdate(
                    it.createdAt,
                    it.update,
                    it.extra
                )
            }.toList(),
            doc.data!!,
            doc.createdAt,
            run {
                val myself = when (card) {
                    null -> emptyList()
                    IdCard.System -> emptyList()

                    is IdCard.Provider -> {
                        listOf(Permission.PROVIDER, Permission.READ, Permission.EDIT)
                    }

                    is IdCard.User -> {
                        if (doc.project == 0 && card.uid == doc.createdBy) {
                            listOf(Permission.ADMIN, Permission.READ, Permission.EDIT)
                        } else if (card.adminOf.contains(doc.project)) {
                            listOf(Permission.ADMIN, Permission.READ, Permission.EDIT)
                        } else {
                            val permissions = HashSet<Permission>()
                            for (entry in doc.acl) {
                                if (entry == null) break

                                if (entry.isUser && entry.entity == card.uid) {
                                    permissions.add(entry.permission)
                                } else if (!entry.isUser && card.groups.contains(entry.entity)) {
                                    permissions.add(entry.permission)
                                }
                            }

                            permissions.toList()
                        }
                    }
                }

                val others = HashMap<AclEntity, HashSet<Permission>>()
                for (entry in doc.acl) {
                    if (entry == null) break

                    val entity = if (entry.isUser) {
                        AclEntity.User(idCards.lookupUid(entry.entity) ?: error("Invalid UID in ACL? ${entry.entity}"))
                    } else {
                        idCards.lookupGid(entry.entity) ?: error("Invalid GID in ACL? ${entry.entity}")
                    }

                    val set = others[entity] ?: HashSet<Permission>().also { others[entity] = it }
                    set.add(entry.permission)
                }

                ResourcePermissions(
                    myself,
                    others.map { ResourceAclEntry(it.key, it.value.toList()) }
                )
            },
            resolvedProduct,
            try {
                providers.retrieveSupport(Jobs, resolvedProduct.category.provider)
                    .find { productCache.referenceToProductId(it.product) == doc.product }
                    ?.let { support ->
                        ResolvedSupport(
                            resolvedProduct,
                            support
                        )
                    }
            } catch (ex: Throwable) {
                null
            }
        ).converter()
    }

    data class GenericResourceUpdate(
        override val timestamp: Long,
        override val status: String?,
        val extra: JsonElement?,
    ) : ResourceUpdate

    data class DocMapperInfo<InternalState>(
        val doc: ResourceDocument<InternalState>,
        val id: String,
        val owner: ResourceOwner,
        val updates: List<GenericResourceUpdate>,
        val data: InternalState,
        val createdAt: Long,
        val permissions: ResourcePermissions,
        val resolvedProduct: Product,
        val resolvedSupport: ResolvedSupport<Product, ProductSupport>?,
    )
}
