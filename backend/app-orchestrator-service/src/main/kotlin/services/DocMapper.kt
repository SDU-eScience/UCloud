package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
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
    private val api: UnknownResourceApi<*>,
    private val converter: suspend DocMapperInfo<A>.() -> B,
) {
    suspend fun map(
        card: IdCard?,
        doc: ResourceDocument<A>,
        flags: ResourceIncludeFlags? = null,
    ): B {
        val productReference = productCache.productIdToReference(doc.product) ?: error("unknown product: ${doc.product}")
        val resolvedProduct = if (flags != null && !flags.includeProduct && !flags.includeSupport) {
            null
        } else {
            productCache.productIdToProduct(doc.product) ?: error("unknown product: ${doc.product}")
        }

        val resolvedSupport = if (resolvedProduct == null || (flags != null && !flags.includeSupport)) {
            null
        } else {
            try {
                providers.retrieveSupport(api, resolvedProduct.category.provider)
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
        }

        val updates = if (flags != null && !flags.includeUpdates) {
            null
        } else {
            doc.update.asSequence().filterNotNull().map {
                GenericResourceUpdate(
                    it.createdAt,
                    it.update,
                    it.extra
                )
            }.toList()
        }

        val permissions = run {
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

            val others = if (flags != null && !flags.includeOthers) {
                null
            } else {
                val others = HashMap<AclEntity, HashSet<Permission>>()
                for (entry in doc.acl) {
                    val entity = if (entry.isUser) {
                        AclEntity.User(idCards.lookupUid(entry.entity) ?: error("Invalid UID in ACL? ${entry.entity}"))
                    } else {
                        idCards.lookupGid(entry.entity) ?: error("Invalid GID in ACL? ${entry.entity}")
                    }

                    val set = others[entity] ?: HashSet<Permission>().also { others[entity] = it }
                    set.add(entry.permission)
                }

                others.map { ResourceAclEntry(it.key, it.value.toList()) }
            }

            ResourcePermissions(myself, others)
        }

        val prelimResult = DocMapperInfo<A>(
            doc,
            doc.id.toString(),
            ResourceOwner(
                idCards.lookupUid(doc.createdBy) ?: "_ucloud",
                idCards.lookupPid(doc.project),
            ),
            updates,
            doc.data!!,
            doc.createdAt,
            permissions,
            productReference,
            resolvedProduct,
            resolvedSupport,
            flags,
        ).converter()

        return prelimResult
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
        val updates: List<GenericResourceUpdate>?,
        val data: InternalState,
        val createdAt: Long,
        val permissions: ResourcePermissions,
        val productReference: ProductReference,
        val resolvedProduct: Product?,
        val resolvedSupport: ResolvedSupport<Product, ProductSupport>?,
        val flags: ResourceIncludeFlags?,
    )
}
