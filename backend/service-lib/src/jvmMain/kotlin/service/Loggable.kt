package dk.sdu.cloud.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.companionObject

actual typealias Logger = Logger
actual fun Logger(tag: String): Logger = LoggerFactory.getLogger(tag)

actual fun Loggable.defaultLogger(): Logger {
    return LoggerFactory.getLogger(unwrapCompanionClass(javaClass).name.substringBeforeLast('$'))
}

internal fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> {
    return ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject?.java == ofClass
    } ?: ofClass
}
