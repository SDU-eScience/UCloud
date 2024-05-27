package dk.sdu.cloud

import io.prometheus.client.Gauge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object Metrics {
    fun initCoreMetrics() {
        ProcessingScope.launch {
            while(coroutineContext.isActive) {
                val rt = Runtime.getRuntime()
                maxHeapSize.set(rt.maxMemory().toDouble())
                committedMemory.set(rt.totalMemory().toDouble())
                heapUsed.set(rt.totalMemory().toDouble() - rt.freeMemory().toDouble())

                delay(30_000)
            }
        }
    }

    private val maxHeapSize = Gauge.build()
        .namespace(systemName)
        .subsystem("memory")
        .name("max_heap_size")
        .help("Max heap size of the JVM [Runtime.maxMemory()]")
        .register()

    private val committedMemory = Gauge.build()
        .namespace(systemName)
        .subsystem("memory")
        .name("committed_memory")
        .help("Memory committed by the process [Runtime.totalMemory()]")
        .register()

    private val heapUsed = Gauge.build()
        .namespace(systemName)
        .subsystem("memory")
        .name("heap_used")
        .help("Memory used by the heap [Runtime.totalMemory() - Runtime.freeMemory()]")
        .register()

}