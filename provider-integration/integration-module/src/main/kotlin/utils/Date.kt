package dk.sdu.cloud.utils

import dk.sdu.cloud.service.Time
import io.ktor.server.util.*
import io.ktor.util.date.*
import java.util.*

data class DateDetails(
    val dayOfWeek: DayOfWeek,
    val dayOfMonth: Int,
    val month: Month,
    val year: Int,

    val hours: Int,
    val minutes: Int,
    val seconds: Int,
) {
    fun simpleString(includeTimeOfDay: Boolean = false): String = buildString {
        append(dayOfMonth.toString().padStart(2, '0'))
        append('/')
        append((month.ordinal + 1).toString().padStart(2, '0'))
        append('/')
        append(year)

        if (includeTimeOfDay) {
            append(' ')
            append(hours.toString().padStart(2, '0'))
            append(':')
            append(minutes.toString().padStart(2, '0'))
            append(':')
            append(seconds.toString().padStart(2, '0'))
            append(" GMT")
        }
    }

    override fun toString(): String = simpleString(includeTimeOfDay = true)
}

typealias Month = io.ktor.util.date.Month
typealias DayOfWeek = WeekDay

fun gmtTime(timestamp: Long = Time.now()): DateDetails {
    val d = Date(timestamp).toInstant().toGMTDate()
    return DateDetails(
        d.dayOfWeek,
        d.dayOfMonth,
        d.month,
        d.year,
        d.hours,
        d.minutes,
        d.seconds
    )
}
