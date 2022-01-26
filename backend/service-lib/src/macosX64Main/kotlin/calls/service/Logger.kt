package dk.sdu.cloud.service

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

actual fun Logger(tag: String): Logger = Log(tag)

actual fun Loggable.defaultLogger(): Logger = Logger(this::class.qualifiedName ?: "?")

actual interface Logger {
    actual fun trace(message: String)
    actual fun debug(message: String)
    actual fun info(message: String)
    actual fun warn(message: String)
    actual fun error(message: String)
}

class Log(val tag: String) : Logger {
    override fun trace(message: String) {
        LogManager.log(LogLevel.TRACE, tag, message)
    }

    override fun debug(message: String) {
        LogManager.log(LogLevel.DEBUG, tag, message)
    }

    override fun info(message: String) {
        LogManager.log(LogLevel.INFO, tag, message)
    }

    override fun warn(message: String) {
        LogManager.log(LogLevel.WARN, tag, message)
    }

    override fun error(message: String) {
        LogManager.log(LogLevel.ERROR, tag, message)
    }

    fun message(level: LogLevel, message: String) {
        LogManager.log(level, tag, message)
    }
}

enum class LogLevel(val short: String) {
    TRACE("T"),
    DEBUG("D"),
    INFO("I"),
    WARN("WARNING"),
    ERROR("ERROR")
}

@OptIn(ExperimentalTime::class)
@ThreadLocal private var lastLog: TimeMark? = null
@ThreadLocal var currentLogLevel: LogLevel = LogLevel.TRACE

@OptIn(ExperimentalTime::class)
object LogManager {
    val customLogLevels: MutableMap<String, LogLevel> = HashMap()

    fun log(level: LogLevel, tag: String, message: String) {
        val minLevel = customLogLevels[tag] ?: currentLogLevel
        if (level.ordinal < minLevel.ordinal) return

        val duration = lastLog?.elapsedNow() ?: Duration.ZERO
        lastLog = TimeSource.Monotonic.markNow()
        printlnWithLogColor(level, "[${level.short}/$tag ${duration}] $message")
    }
}

fun printlnWithLogColor(level: LogLevel, message: String) {
    val color = when (level) {
        LogLevel.TRACE -> ANSI_WHITE
        LogLevel.DEBUG -> ANSI_BLUE
        LogLevel.INFO -> ANSI_GREEN
        LogLevel.WARN -> ANSI_YELLOW
        LogLevel.ERROR -> ANSI_RED
    }

    println(color + message + ANSI_RESET)
}
private val ANSI_RESET = "\u001B[0m"
private val ANSI_BLUE = "\u001B[34m"
private val ANSI_RED = "\u001B[31m"
private val ANSI_GREEN = "\u001B[32m"
private val ANSI_YELLOW = "\u001B[33m"
private val ANSI_WHITE = "\u001B[37m"
