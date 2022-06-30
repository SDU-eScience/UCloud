package dk.sdu.cloud.utils

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
)

typealias Month = io.ktor.util.date.Month
typealias DayOfWeek = WeekDay

fun gmtTime(timestamp: Long): DateDetails {
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
