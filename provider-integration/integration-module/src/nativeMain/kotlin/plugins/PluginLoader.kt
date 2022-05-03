package dk.sdu.cloud.plugins

import dk.sdu.cloud.config.*
import dk.sdu.cloud.plugins.compute.slurm.*
import dk.sdu.cloud.plugins.connection.*
import dk.sdu.cloud.plugins.storage.posix.*
import dk.sdu.cloud.plugins.allocations.*
import kotlin.reflect.KClass

private val pluginLookupTable = mapOf<KClass<*>, () -> Plugin<*>>(
    // Connection
    // =========================================================================
    OpenIdConnectConfiguration::class to { OpenIdConnectPlugin() },
    TicketBasedConnectionConfiguration::class to { TicketBasedConnectionPlugin() },
    UCloudConnectionConfiguration::class to { UCloudConnectionPlugin() },

    // Projects
    // =========================================================================
    SimpleProjectConfiguration::class to { SimpleProjectPlugin() },

    // Allocations
    // =========================================================================
    ExtensionAllocationConfig::class to { PosixCollectionPlugin() },

    // Jobs
    // =========================================================================
    SlurmConfig::class to { SlurmPlugin() },

    // Files
    // =========================================================================
    PosixFilesConfiguration::class to { PosixFilesPlugin() },

    // File Collections
    // =========================================================================
    PosixFileCollectionsConfiguration::class to { PosixCollectionPlugin() },
)

fun <Cfg : Any> instansiatePlugin(config: Cfg): Plugin<Cfg> {
    val instansiator = pluginLookupTable[config::class]
        ?: error("Plugin missing from pluginLookupTable in PluginLoader.kt (${config::class})")

    return instansiator() as Plugin<Cfg>
}

