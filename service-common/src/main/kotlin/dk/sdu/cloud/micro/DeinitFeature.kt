package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString

class DeinitFeature : MicroFeature {
    private val handlers = ArrayList<() -> Unit>()
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {

    }

    fun addHandler(handler: () -> Unit) {
        handlers.add(handler)
    }

    fun runHandlers() {
        log.info("Shutting down features")
        handlers.forEach {
            try {
                it()
            } catch (ex: Throwable) {
                log.warn("Caught exception while running de-init handler!")
                log.warn(ex.stackTraceToString())
            }
        }
        log.info("Ran ${handlers.size} handlers")
    }

    companion object Feature : MicroFeatureFactory<DeinitFeature, Unit>, Loggable {
        override val key = MicroAttributeKey<DeinitFeature>("deinit-feature")
        override fun create(config: Unit): DeinitFeature = DeinitFeature()

        override val log = logger()
    }
}
