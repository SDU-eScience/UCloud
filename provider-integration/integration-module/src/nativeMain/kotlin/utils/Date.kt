package dk.sdu.cloud.utils

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import platform.posix.gmtime

data class DateDetails(
    val dayOfWeek: DayOfWeek,
    val dayOfMonth: Int,
    val month: Month,
    val year: Int,

    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

enum class Month(val numericValue: Int) {
    JANUARY(1),
    FEBRUARY(2),
    MARCH(3),
    APRIL(4),
    MAY(5),
    JUNE(6),
    JULY(7),
    AUGUST(8),
    SEPTEMBER(9),
    OCTOBER(10),
    NOVEMBER(11),
    DECEMBER(12),
}

enum class DayOfWeek {
    SUNDAY,
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
}

fun gmtTime(timestamp: Long): DateDetails {
    return memScoped {
        val time = longArrayOf(timestamp / 1000L).pin()
        val st = gmtime(time.addressOf(0)) ?: error("Could not retrieve the time")
        time.unpin()
        val tm = st.pointed
        val dayOfWeek = when (tm.tm_wday) {
            0 -> DayOfWeek.SUNDAY
            1 -> DayOfWeek.MONDAY
            2 -> DayOfWeek.TUESDAY
            3 -> DayOfWeek.WEDNESDAY
            4 -> DayOfWeek.THURSDAY
            5 -> DayOfWeek.FRIDAY
            6 -> DayOfWeek.SATURDAY

            else -> DayOfWeek.MONDAY
        }

        val dayOfMonth = tm.tm_mday

        val month = when (tm.tm_mon) {
            0 -> Month.JANUARY
            1 -> Month.FEBRUARY
            2 -> Month.MARCH
            3 -> Month.APRIL
            4 -> Month.MAY
            5 -> Month.JUNE
            6 -> Month.JULY
            7 -> Month.AUGUST
            8 -> Month.SEPTEMBER
            9 -> Month.OCTOBER
            10 -> Month.NOVEMBER
            11 -> Month.DECEMBER

            else -> Month.JANUARY
        }

        val year = tm.tm_year + 1900
        val hour = tm.tm_hour
        val minutes = tm.tm_min
        val seconds = tm.tm_sec

        DateDetails(dayOfWeek, dayOfMonth, month, year, hour, minutes, seconds)
    }
}
