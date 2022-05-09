package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.config.*
import dk.sdu.cloud.file.orchestrator.api.FSCollectionSupport
import dk.sdu.cloud.file.orchestrator.api.FSFileSupport
import dk.sdu.cloud.file.orchestrator.api.FSProductStatsSupport
import dk.sdu.cloud.file.orchestrator.api.FSSupport
import dk.sdu.cloud.plugins.FileCollectionPlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.extension
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.provider.api.ResourceOwner
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import platform.posix.geteuid
import platform.posix.getpwuid

class PosixCollectionPlugin : FileCollectionPlugin {
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    private lateinit var pathConverter: PathConverter
    private lateinit var pluginConfig: PosixFileCollectionsConfiguration
    private var initializedProjects = HashSet<String?>()
    private val mutex = Mutex()

    override fun configure(config: ConfigSchema.Plugins.FileCollections) {
        this.pluginConfig = config as PosixFileCollectionsConfiguration
    }

    override suspend fun PluginContext.init(owner: ResourceOwner) {
        pathConverter = PathConverter(this)
        val project = owner.project
        mutex.withLock {
            if (project in initializedProjects) return
            if (!initializedProjects.add(project)) return
        }

        data class CollWithProduct(
            val title: String,
            val pathPrefix: String,
            val product: ProductReferenceWithoutProvider
        )

        val homes = HashMap<String, CollWithProduct>()
        val projects = HashMap<String, CollWithProduct>()

        val collections = ArrayList<PathConverter.Collection>()

        run {
            val product = productAllocation.firstOrNull() ?: return@run

            run {
                // Simple mappers
                pluginConfig.simpleHomeMapper.forEach { home ->
                    homes[home.prefix] = CollWithProduct(home.title, home.prefix, product)
                }

                if (project == null && homes.isNotEmpty()) {
                    val username = run {
                        val uid = geteuid()
                        getpwuid(uid)?.pointed?.pw_name?.toKStringFromUtf8() ?: "$uid"
                    }

                    homes.forEach { (_, coll) ->
                        val mappedPath = coll.pathPrefix.removeSuffix("/") + "/" + username
                        collections.add(
                            PathConverter.Collection(owner, coll.title, mappedPath, coll.product)
                        )
                    }
                }
            }

            run {
                // Extensions
                val extension = pluginConfig.extensions.additionalCollections
                if (extension != null) {
                    retrieveCollections.invoke(extension, owner).forEach {
                        collections.add(
                            PathConverter.Collection(owner, it.title, it.path, product, it.balance)
                        )
                    }
                }
            }
        }

        if (collections.isNotEmpty()) {
            pathConverter.registerCollectionWithUCloud(collections)
        }
    }

    override suspend fun PluginContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<FSSupport> {
        return BulkResponse(productAllocation.map { ref ->
            FSSupport(
                ProductReference(ref.id, ref.category, config.core.providerId),
                FSProductStatsSupport(),
                FSCollectionSupport(),
                FSFileSupport()
            )
        })
    }

    private companion object Extensions {
        val retrieveCollections = extension<ResourceOwner, List<PosixCollectionFromExtension>>()
    }
}

@Serializable
private data class PosixCollectionFromExtension(
    val path: String,
    val title: String,
    val balance: Long? = null,
)
