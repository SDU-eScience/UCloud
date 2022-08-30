package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.Ingress
import dk.sdu.cloud.app.orchestrator.api.IngressSupport
import dk.sdu.cloud.config.ConfigSchema

interface IngressPlugin : ResourcePlugin<Product.Ingress, IngressSupport, Ingress, ConfigSchema.Plugins.Ingresses> {

}
