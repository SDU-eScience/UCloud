package dk.sdu.cloud.accounting.api

import kotlin.math.min

private typealias EventChart = Chart<ChartDataPoint2D<Long, Long>>

object ChartingHelpers {
    const val HINT_LINE_CHART = "line-chart"
    const val HINT_BAR_CHART = "bar-chart"
    const val HINT_PIE_CHART = "pie-chart"

    fun <E : AccountingEvent> sumChartFromEvents(
        events: List<E>,
        desiredDataPoints: Int = 25,
        dataType: String = ChartDataTypes.NUMBER,
        dataTitle: String? = null,
        dataLabelSelector: (Long) -> String? = { null },
        dataSelector: (E) -> Long
    ): EventChart {
        if (desiredDataPoints <= 0) throw IllegalArgumentException("desiredDataPoints must be positive")
        if (events.isEmpty()) return emptyChart(dataType, dataTitle)

        with(createPartitioning(events, desiredDataPoints)) {
            val dataPoints = ArrayList<ChartDataPoint2D<Long, Long>>()
            buckets.forEachIndexed { index, bucket ->
                val bucketValue = bucket.asSequence().map(dataSelector).sum()
                val previousBucketValue = if (index == 0) 0L else dataPoints[index - 1].y
                val totalBucketValue = bucketValue + previousBucketValue
                dataPoints.add(
                    DataPoint2D(
                        minTimestamp + (timePerBucket * index),
                        totalBucketValue,
                        dataLabelSelector(totalBucketValue)
                    )
                )
            }

            return Chart(dataPoints, HINT_LINE_CHART, listOf(ChartDataTypes.DATETIME, dataType), dataTitle)
        }
    }

    fun <E : AccountingEvent> absoluteChartFromEvents(
        events: List<E>,
        desiredDataPoints: Int = 25,
        dataType: String = ChartDataTypes.NUMBER,
        dataTitle: String? = null,
        dataLabelSelector: (Long) -> String? = { null },
        dataSelector: (E) -> Long
    ): EventChart {
        if (desiredDataPoints <= 0) throw IllegalArgumentException("desiredDataPoints must be positive")
        if (events.isEmpty()) return emptyChart(dataType, dataTitle)
        with(createPartitioning(events, desiredDataPoints)) {
            val dataPoints = ArrayList<ChartDataPoint2D<Long, Long>>()
            buckets.forEachIndexed { index, bucket ->
                val previousBucketValue = if (index == 0) 0L else dataPoints[index - 1].y
                val bucketValue = if (bucket.isEmpty()) previousBucketValue else dataSelector(bucket.first())
                dataPoints.add(
                    DataPoint2D(
                        minTimestamp + (timePerBucket * index),
                        bucketValue,
                        dataLabelSelector(bucketValue)
                    )
                )
            }

            return Chart(dataPoints, HINT_LINE_CHART, listOf(ChartDataTypes.DATETIME, dataType), dataTitle)
        }
    }

    private class EventPartitioning<E : AccountingEvent>(
        val minTimestamp: Long,
        val maxTimestamp: Long,
        val timespan: Long,
        val timePerBucket: Long,
        val buckets: Array<ArrayList<E>>
    )

    private fun <E : AccountingEvent> createPartitioning(
        events: List<E>,
        desiredDataPoints: Int
    ): EventPartitioning<E> {
        if (desiredDataPoints <= 0) throw IllegalArgumentException("desiredDataPoints must be positive")

        val minTimestamp = events.minBy { it.timestamp }!!.timestamp
        val maxTimestamp = events.maxBy { it.timestamp }!!.timestamp
        val timespan = maxTimestamp - minTimestamp
        val timePerBucket = timespan / desiredDataPoints // This might be 0

        // Initial partitioning of events (based on timestamps)
        val buckets = Array(desiredDataPoints) { ArrayList<E>() }
        events.forEach {
            val relativeTimestamp = it.timestamp - minTimestamp

            val bucketIdx =
                if (timePerBucket == 0L) 0 else min(desiredDataPoints - 1, (relativeTimestamp / timePerBucket).toInt())

            buckets[bucketIdx].add(it)
        }

        return EventPartitioning(minTimestamp, maxTimestamp, timespan, timePerBucket, buckets)
    }

    private fun emptyChart(
        dataType: String = ChartDataTypes.NUMBER,
        dataTitle: String? = null
    ): EventChart {
        return Chart(
            emptyList(),
            HINT_LINE_CHART,
            listOf(ChartDataTypes.DATETIME, dataType),
            dataTitle
        )
    }
}
