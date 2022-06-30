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
    ConfigSchema.Plugins.Connection.OpenIdConnect::class to { OpenIdConnectPlugin() },
    ConfigSchema.Plugins.Connection.Ticket::class to { TicketBasedConnectionPlugin() },
    ConfigSchema.Plugins.Connection.UCloud::class to { UCloudConnectionPlugin() },

    // Projects
    // =========================================================================
    ConfigSchema.Plugins.Projects.Simple::class to { SimpleProjectPlugin() },

    // Allocations
    // =========================================================================
    ConfigSchema.Plugins.Allocations.Extension::class to { ExtensionAllocationPlugin() },

    // Jobs
    // =========================================================================
    SlurmConfig::class to { SlurmPlugin() },

    // Files
    // =========================================================================
    ConfigSchema.Plugins.Files.Posix::class to { PosixFilesPlugin() },

    // File Collections
    // =========================================================================
    ConfigSchema.Plugins.FileCollections.Posix::class to { PosixCollectionPlugin() },
)

fun <Cfg : Any> instantiatePlugin(config: Cfg): Plugin<Cfg> {
    val instantiator = pluginLookupTable[config::class]
        ?: error("Plugin missing from pluginLookupTable in PluginLoader.kt (${config::class})")

    return instantiator() as Plugin<Cfg>
}

