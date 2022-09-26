package dk.sdu.cloud.plugins

import dk.sdu.cloud.config.*
import dk.sdu.cloud.plugins.compute.slurm.*
import dk.sdu.cloud.plugins.connection.*
import dk.sdu.cloud.plugins.storage.posix.*
import dk.sdu.cloud.plugins.allocations.*
import dk.sdu.cloud.plugins.compute.ucloud.UCloudComputePlugin
import dk.sdu.cloud.plugins.compute.ucloud.UCloudIngressPlugin
import dk.sdu.cloud.plugins.compute.ucloud.UCloudPublicIPPlugin
import dk.sdu.cloud.plugins.licenses.generic.GenericLicensePlugin
import dk.sdu.cloud.plugins.puhuri.*
import dk.sdu.cloud.plugins.projects.SimpleProjectPlugin
import dk.sdu.cloud.plugins.storage.ucloud.UCloudFileCollectionPlugin
import dk.sdu.cloud.plugins.storage.ucloud.UCloudFilePlugin
import dk.sdu.cloud.plugins.storage.ucloud.UCloudSharePlugin
import kotlin.reflect.KClass

private val pluginLookupTable = mapOf<KClass<*>, () -> Plugin<*>>(
    // Connection
    // ================================================================================================================
    ConfigSchema.Plugins.Connection.OpenIdConnect::class to { OpenIdConnectPlugin() },
    ConfigSchema.Plugins.Connection.Ticket::class to { TicketBasedConnectionPlugin() },
    ConfigSchema.Plugins.Connection.UCloud::class to { UCloudConnectionPlugin() },

    // Projects
    // ================================================================================================================
    ConfigSchema.Plugins.Projects.Simple::class to { SimpleProjectPlugin() },
    ConfigSchema.Plugins.Projects.Puhuri::class to { PuhuriPlugin() },

    // Allocations
    // ================================================================================================================
    ConfigSchema.Plugins.Allocations.Extension::class to { ExtensionAllocationPlugin() },
    ConfigSchema.Plugins.Allocations.Puhuri::class to { PuhuriAllocationPlugin() },

    // Jobs
    // ================================================================================================================
    SlurmConfig::class to { SlurmPlugin() },
    ConfigSchema.Plugins.Jobs.Puhuri::class to { PuhuriComputePlugin() },
    ConfigSchema.Plugins.Jobs.UCloud::class to { UCloudComputePlugin() },

    // Ingress
    // ================================================================================================================
    ConfigSchema.Plugins.Ingresses.UCloud::class to { UCloudIngressPlugin() },

    // Public IPs
    // ================================================================================================================
    ConfigSchema.Plugins.PublicIPs.UCloud::class to { UCloudPublicIPPlugin() },

    // Licenses
    // ================================================================================================================
    ConfigSchema.Plugins.Licenses.Generic::class to { GenericLicensePlugin() },

    // Files
    // ================================================================================================================
    ConfigSchema.Plugins.Files.Posix::class to { PosixFilesPlugin() },
    ConfigSchema.Plugins.Files.Puhuri::class to { PuhuriFilePlugin() },
    ConfigSchema.Plugins.Files.UCloud::class to { UCloudFilePlugin() },
    
    // Shares
    // ================================================================================================================
    ConfigSchema.Plugins.Shares.UCloud::class to { UCloudSharePlugin() },

    // File Collections
    // ================================================================================================================
    ConfigSchema.Plugins.FileCollections.Posix::class to { PosixCollectionPlugin() },
    ConfigSchema.Plugins.FileCollections.Puhuri::class to { PuhuriFileCollectionPlugin() },
    ConfigSchema.Plugins.FileCollections.UCloud::class to { UCloudFileCollectionPlugin() },
)

fun <Cfg : Any> instantiatePlugin(config: Cfg): Plugin<Cfg> {
    val instantiator = pluginLookupTable[config::class]
        ?: error("Plugin missing from pluginLookupTable in PluginLoader.kt (${config::class})")

    return instantiator() as Plugin<Cfg>
}

