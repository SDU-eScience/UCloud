package dk.sdu.cloud.accounting.api

object ChartingHelpers {
    const val HINT_LINE_CHART = "line-chart"
    const val HINT_BAR_CHART = "bar-chart"
    const val HINT_PIE_CHART = "pie-chart"

    inline fun <E : AccountingEvent> basicChartFromEvents(
        events: List<E>,
        desiredDataPoints: Int = 25,
        xAxisLabel: String = "Time",
        yAxisLabel: String = "Value",
        labelSelector: (Long) -> String? = { null },
        noinline dataSelector: (E) -> Long
    ): Chart<ChartDataPoint2D<Long, Long>> {
        if (desiredDataPoints <= 0) throw IllegalArgumentException("desiredDataPoints must be positive")
        if (events.isEmpty()) return Chart(xAxisLabel, yAxisLabel, emptyList())

        val minTimestamp = events.minBy { it.timestamp }!!.timestamp
        val maxTimestamp = events.maxBy { it.timestamp }!!.timestamp
        val timespan = maxTimestamp - minTimestamp
        val timePerBucket = timespan / desiredDataPoints // This might be 0

        // Initial partitioning of events (based on timestamps)
        val buckets = Array(desiredDataPoints) { ArrayList<E>() }
        events.forEach {
            val relativeTimestamp = it.timestamp - minTimestamp
            val bucketIdx = if (timePerBucket == 0L) 0 else (relativeTimestamp / timePerBucket).toInt()

            buckets[bucketIdx].add(it)
        }

        val dataPoints = ArrayList<ChartDataPoint2D<Long, Long>>()
        buckets.forEachIndexed { index, bucket ->
            val bucketValue = bucket.asSequence().map(dataSelector).sum()
            val previousBucketValue = if (index == 0) 0L else dataPoints[index - 1].y
            val totalBucketValue = bucketValue + previousBucketValue

            dataPoints.add(
                DataPoint2D(
                    minTimestamp + (timePerBucket * index),
                    totalBucketValue,
                    labelSelector(totalBucketValue)
                )
            )
        }

        return Chart(xAxisLabel, yAxisLabel, dataPoints, chartTypeHint = HINT_LINE_CHART)
    }
}
