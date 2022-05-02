package dk.sdu.cloud.plugins.storage

import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.RegisterWalletRequestItem
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.SimpleCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

value class InternalFile(val path: String)
value class UCloudFile private constructor(val path: String) {
    companion object {
        fun create(path: String) = UCloudFile(path.normalize())
        fun createFromPreNormalizedString(path: String) = UCloudFile(path)
    }
}

class PathConverter(private val ctx: PluginContext) {
    private val idPrefix = ctx.config.core.providerId + "-"

    data class Collection(
        val owner: ResourceOwner,
        val title: String,
        val localPath: String,
        val product: ProductReferenceWithoutProvider,
        val balance: Long? = null,
    )

    val collectionCache = SimpleCache<String, FileCollection>(
        maxAge = 60_000 * 10L,
        lookup = { collectionId ->
            FileCollectionsControl.retrieve.call(
                ResourceRetrieveRequest(FileCollectionIncludeFlags(), collectionId),
                ctx.rpcClient
            ).orThrow()
        }
    )

    private val cachedProviderIdsMutex = Mutex()
    private val cachedProviderIds = HashMap<String, FileCollection>()

    suspend fun ucloudToCollection(uCloudFile: UCloudFile): FileCollection {
        val collectionID = uCloudFile.path.components().firstOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return collectionCache.get(collectionID) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }

    suspend fun registerCollectionWithUCloud(collections: List<Collection>) {
        FileCollectionsControl.register.call(
            BulkRequest(
                collections.map { home ->
                    ProviderRegisteredResource(
                        FileCollection.Spec(
                            home.title,
                            ProductReference(home.product.id, home.product.category, ctx.config.core.providerId),
                        ),
                        idPrefix + home.localPath,
                        home.owner.createdBy,
                        home.owner.project
                    )
                }
            ),
            ctx.rpcClient
        ).orThrow()

        val registration = collections.mapNotNull { home ->
            if (home.balance == null) return@mapNotNull null
            RegisterWalletRequestItem(
                if (home.owner.project != null) WalletOwner.Project(home.owner.project!!)
                else WalletOwner.User(home.owner.createdBy),
                buildString {
                    append(home.owner.project ?: home.owner.createdBy)
                    append('-')
                    append(home.localPath)
                },
                home.product.category,
                home.balance
            )
        }

        if (registration.isNotEmpty()) {
            Wallets.register.call(
                BulkRequest(registration),
                ctx.rpcClient
            ).orThrow()
        }
    }

    private fun lookupCollectionFromPath(internalFile: InternalFile): FileCollection {
        val parents = internalFile.path.parents() + listOf(internalFile.path)
        for (parent in parents) {
            val cached = cachedProviderIds[parent.removeSuffix("/")]
            if (cached != null) {
                return cached
            }
        }

        return runBlocking {
            val potentialProviderIds = parents.joinToString(",") { idPrefix + it.normalize() }
            val collection = FileCollectionsControl.browse.call(
                ResourceBrowseRequest(
                    FileCollectionIncludeFlags(filterProviderIds = potentialProviderIds)
                ),
                ctx.rpcClient
            ).orRethrowAs {
                throw RPCException("Could not fetch data about our collections", HttpStatusCode.BadGateway)
            }.items.maxByOrNull { it.providerGeneratedId?.length ?: 0 }
                ?: throw RPCException("Collection does not exist: $internalFile", HttpStatusCode.InternalServerError)

            val collProviderId = collection.providerGeneratedId
            if (collProviderId != null) {
                cachedProviderIdsMutex.withLock {
                    cachedProviderIds[collProviderId.removePrefix(idPrefix)] = collection
                }
            }

            collection
        }
    }

    suspend fun ucloudToInternal(file: UCloudFile): InternalFile {
        val components = file.path.normalize().components()
        val collectionId = components[0]
        val withoutCollection = components.drop(1)

        val collection = collectionCache.get(collectionId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val providerGeneratedId = collection.providerGeneratedId

        if (providerGeneratedId != null && providerGeneratedId.startsWith(idPrefix)) {
            cachedProviderIdsMutex.withLock {
                cachedProviderIds[providerGeneratedId] = collection
            }

            val prefix = providerGeneratedId.removePrefix(idPrefix).removeSuffix("/")
            return InternalFile(buildString {
                append(prefix)
                withoutCollection.forEach { part ->
                    append('/')
                    append(part)
                }
            })
        } else {
            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    fun internalToUCloud(file: InternalFile): UCloudFile {
        val components = file.path.normalize().components()
        if (components.size <= 1) throw RPCException("Not a valid file", HttpStatusCode.InternalServerError)

        return UCloudFile.createFromPreNormalizedString(
            buildString {

                val collection = lookupCollectionFromPath(file)
                val providerId = collection.providerGeneratedId
                    ?: throw RPCException("Unexpected result from UCloud", HttpStatusCode.BadGateway)

                val startIdx = providerId.removePrefix(idPrefix).normalize().count { it == '/' }
                append('/')
                append(collection.id)
                for ((idx, component) in components.withIndex()) {
                    if (idx < startIdx) continue
                    append('/')
                    append(component)
                }
            }
        )
    }
}

