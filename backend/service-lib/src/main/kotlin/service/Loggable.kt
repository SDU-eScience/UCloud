package dk.sdu.cloud.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface Loggable {
    val log: Logger
    fun logger(): Logger = defaultLogger()
}

typealias Logger = Logger
fun Logger(tag: String): Logger = LoggerFactory.getLogger(tag)

fun Loggable.defaultLogger(): Logger {
    return LoggerFactory.getLogger(javaClass.name.substringBeforeLast('$'))
}
