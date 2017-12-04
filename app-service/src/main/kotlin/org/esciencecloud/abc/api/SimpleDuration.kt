package org.esciencecloud.abc.api

data class SimpleDuration(val hours: Int, val minutes: Int, val seconds: Int) {
    init {
        if (seconds !in 0..59) throw IllegalArgumentException("seconds must be in 0..59")
        if (minutes !in 0..59) throw IllegalArgumentException("minutes must be in 0..59")
    }

    override fun toString() = StringBuilder().apply {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }.toString()
}