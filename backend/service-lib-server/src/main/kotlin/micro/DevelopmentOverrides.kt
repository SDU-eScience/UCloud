package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable

class DevelopmentOverrides : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)
        ctx.developmentModeEnabled = cliArgs.contains("--dev")
    }

    companion object Feature : MicroFeatureFactory<DevelopmentOverrides, Unit>,
        Loggable {
        override val key: MicroAttributeKey<DevelopmentOverrides> =
            MicroAttributeKey("development-overrides")
        override val log = logger()

        override fun create(config: Unit): DevelopmentOverrides =
            DevelopmentOverrides()

        internal val ENABLED_KEY = MicroAttributeKey<Boolean>("development-mode")
    }
}

var Micro.developmentModeEnabled: Boolean
    get() {
        return attributes.getOrNull(DevelopmentOverrides.ENABLED_KEY) ?: false
    }
    internal set(value) {
        attributes[DevelopmentOverrides.ENABLED_KEY] = value
    }
