package dk.sdu.cloud

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Summary
import io.prometheus.client.exporter.common.TextFormat
import java.io.Writer

object Prometheus {
    const val metricsEndpoint = "/metrics"
    const val metricsPort = 7867

    data class Responder(
        val contentType: String,
        val generator: (writer: Writer) -> Unit,
    )

    fun respondToPrometheusQuery(acceptHeader: String?): Responder {
        val format = TextFormat.chooseContentType(acceptHeader)

        val generator: (Writer) -> Unit = { writer ->
            TextFormat.writeFormat(
                format,
                writer,
                CollectorRegistry.defaultRegistry.metricFamilySamples()
            )
        }

        return Responder(format, generator)
    }

    // Shared metrics (which aren't better suited to be placed in a different component)
    private val backgroundTaskIterationCounter by lazy {
        Counter.build()
            .namespace(systemName) // lazy to make sure that this has been updated
            .subsystem("background_tasks")
            .name("total_iterations")
            .help("Number of iterations since startup")
            .labelNames("task")
            .register()
    }

    private val backgroundTaskIterationDuration by lazy {
        Summary.build()
            .namespace(systemName) // lazy to make sure that this has been updated
            .subsystem("background_tasks")
            .name("iteration_duration_milliseconds")
            .help("Summary of the duration it takes to complete a single iteration of a background task")
            .labelNames("task")
            .quantile(0.5, 0.01)
            .quantile(0.75, 0.01)
            .quantile(0.95, 0.01)
            .register()
    }

    fun countBackgroundTask(taskName: String) {
        backgroundTaskIterationCounter.labels(taskName).inc()
    }

    fun measureBackgroundDuration(taskName: String, timeInMilliseconds: Long) {
        backgroundTaskIterationDuration.labels(taskName).observe(timeInMilliseconds.toDouble())
    }
}
