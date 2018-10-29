package dk.sdu.cloud.accounting.api

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object AccountingHelpers {
    private val periodTimeZone = ZoneId.of("Europe/Copenhagen")

    /**
     * Returns the start of the period activate [atTimestamp]
     *
     * Format is an epoch milliseconds timestamp
     *
     * @param atTimestamp epoch millis timestamp
     */
    fun startOfPeriod(atTimestamp: Long = System.currentTimeMillis()): Long {
        return Instant
            .ofEpochMilli(atTimestamp)
            .atZone(periodTimeZone)
            .with(TemporalAdjusters.firstDayOfMonth())
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Returns the start of the period activate [atTimestamp]
     *
     * Format is an epoch milliseconds timestamp
     *
     * @param atTimestamp epoch millis timestamp
     */
    fun endOfPeriod(atTimestamp: Long = System.currentTimeMillis()): Long {
        return Instant
            .ofEpochMilli(atTimestamp)
            .atZone(periodTimeZone)
            .with(TemporalAdjusters.firstDayOfNextMonth())
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .truncatedTo(ChronoUnit.SECONDS)
            .toInstant()
            .toEpochMilli() - 1
    }
}
