package dk.sdu.cloud.service

interface Loggable {
    val log: Logger
    fun logger(): Logger = defaultLogger()
}

expect fun Logger(tag: String): Logger

expect fun Loggable.defaultLogger(): Logger

expect interface Logger {
    fun trace(message: String)
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}
