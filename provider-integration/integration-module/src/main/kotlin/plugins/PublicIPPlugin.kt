package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.NetworkIP
import dk.sdu.cloud.app.orchestrator.api.NetworkIPSupport
import dk.sdu.cloud.config.ConfigSchema

interface PublicIPPlugin : ResourcePlugin<Product.NetworkIP, NetworkIPSupport, NetworkIP,
        ConfigSchema.Plugins.PublicIPs> {

}
