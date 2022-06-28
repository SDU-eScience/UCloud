package dk.sdu.cloud.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory

actual typealias Logger = Logger
actual fun Logger(tag: String): Logger = LoggerFactory.getLogger(tag)

actual fun Loggable.defaultLogger(): Logger {
    return LoggerFactory.getLogger(javaClass.name.substringBeforeLast('$'))
}
