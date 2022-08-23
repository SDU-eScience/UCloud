package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.License
import dk.sdu.cloud.app.orchestrator.api.LicenseSupport
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.config.ConfigSchema

interface LicensePlugin : ResourcePlugin<Product.License, LicenseSupport, License, ConfigSchema.Plugins.Licenses> {
    suspend fun buildParameter(param: AppParameterValue.License): String
}
