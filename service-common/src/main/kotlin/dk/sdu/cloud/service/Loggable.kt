package dk.sdu.cloud.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.companionObject

interface Loggable {
    val log: Logger

    fun logger(): Logger {
        return LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).name)
    }
}

internal fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> {
    return ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject?.java == ofClass
    } ?: ofClass
}