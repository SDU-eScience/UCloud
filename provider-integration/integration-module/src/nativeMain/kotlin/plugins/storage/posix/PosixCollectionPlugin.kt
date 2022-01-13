package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.completeConfiguration
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.FileCollectionPlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.storage.InternalFile
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.provider.api.ResourceOwner
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import platform.posix.geteuid
import platform.posix.getpwuid

@Serializable
data class PredefinedCollection(
    val title: String,
    val prefix: String
)

@Serializable
data class PosixPluginConfiguration(
    /**
     * Maps a simple home folder. Each string represents a parent directory, for example "/home". The plugin will use
     * the local username to determine the actual collection.
     *
     * Example:
     *
     * ```kotlin
     * // Given:
     * val simpleHomeMapper = listOf(PredefinedCollection("Home", "/home"), PredefinedCollection("Fast Home", "/fast/home"))
     * val localUsername = "DanThrane"
     *
     * // Will result in:
     * val resolvedCollection = listOf("/home/DanThrane", "/fast/home/DanThrane")
     * ```
     */
    val simpleHomeMapper: List<PredefinedCollection> = emptyList(),

    /**
     * Similar to [simpleHomeMapper] but uses the UCloud project ID instead of the local username.
     */
    val simpleProjectMapper: List<PredefinedCollection> = emptyList(),
)

class PosixCollectionPlugin : FileCollectionPlugin {
    private lateinit var pathConverter: PathConverter
    private lateinit var pluginConfig: ProductBasedConfiguration
    private var initializedProjects = HashSet<String?>()
    private val mutex = Mutex()

    override suspend fun PluginContext.init(owner: ResourceOwner) {
        pathConverter = PathConverter(this)
        val project = owner.project
        mutex.withLock {
            if (project in initializedProjects) return
            if (!initializedProjects.add(project)) return
        }

        val fullConfig = pluginConfig.completeConfiguration<PosixPluginConfiguration>()

        data class CollWithProduct(
            val title: String,
            val pathPrefix: String,
            val product: ProductReferenceWithoutProvider
        )

        val homes = HashMap<String, CollWithProduct>()
        val projects = HashMap<String, CollWithProduct>()
        fullConfig.forEach { (cfg, products) ->
            val product = products.firstOrNull() ?: return@forEach
            cfg.simpleHomeMapper.forEach { home ->
                homes[home.prefix] = CollWithProduct(home.title, home.prefix, product)
            }
            cfg.simpleProjectMapper.forEach { project ->
                homes[project.prefix] = CollWithProduct(project.title, project.prefix, product)
            }
        }

        if (project == null && homes.isNotEmpty()) {
            val username = run {
                val uid = geteuid()
                getpwuid(uid)?.pointed?.pw_name?.toKStringFromUtf8() ?: "$uid"
            }

            pathConverter.registerCollectionWithUCloud(
                homes.map { (_, coll) ->
                    val mappedPath = coll.pathPrefix.removeSuffix("/") + "/" + username
                    PathConverter.Collection(owner, coll.title, mappedPath, coll.product)
                }
            )
        } else if (project != null && projects.isNotEmpty()) {
            pathConverter.registerCollectionWithUCloud(
                projects.map { (_, coll) ->
                    val mappedPath = coll.pathPrefix.removeSuffix("/") + "/" + owner.project
                    PathConverter.Collection(owner, coll.title, mappedPath, coll.product)
                }
            )
        }
    }

    override suspend fun PluginContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<FSSupport> {
        return BulkResponse(pluginConfig.products.map { ref ->
            FSSupport(
                ProductReference(ref.id, ref.category, config.core.providerId),
                FSProductStatsSupport(),
                FSCollectionSupport(),
                FSFileSupport()
            )
        })
    }

    override suspend fun PluginContext.initialize(pluginConfig: ProductBasedConfiguration) {
        this@PosixCollectionPlugin.pluginConfig = pluginConfig
    }
}
