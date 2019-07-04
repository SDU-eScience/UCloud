package dk.sdu.cloud.accounting.compute.services

import dk.sdu.cloud.app.store.api.SimpleDuration

const val SECONDS_MS = 1000L
const val MINUTES_MS = SECONDS_MS * 60
const val HOURS_MS = MINUTES_MS * 60

fun Long.toSimpleDuration(): SimpleDuration {
    var remaining = this

    val hours = remaining / HOURS_MS
    remaining %= HOURS_MS

    val minutes = remaining / MINUTES_MS
    remaining %= MINUTES_MS

    val seconds = remaining / SECONDS_MS

    return SimpleDuration(hours.toInt(), minutes.toInt(), seconds.toInt())
}
