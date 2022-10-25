package dk.sdu.cloud.utils

import dk.sdu.cloud.service.Logger

typealias FeatureTracer = (message: () -> Any?) -> Unit
fun createFeatureTracer(enabled: Boolean, name: String): FeatureTracer {
    if (!enabled) return disabledTracer
    val log = Logger("Feature-$name")
    return { message -> log.info("${message()}") }
}

private val disabledTracer: FeatureTracer = {}

var shellTracer: FeatureTracer = disabledTracer