package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.CpuAndMemory
import dk.sdu.cloud.app.orchestrator.api.QueueStatus
import dk.sdu.cloud.utils.forEachGraal
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max

/**
 * A service responsible for fetching utilization information
 */
class UtilizationService(
    private val k8: K8Dependencies,
    private val runtime: ContainerRuntime,
) {
    suspend fun retrieveCapacity(productCategoryId: String): CpuAndMemory {
        val relevantNodes = runtime.listNodes().filter { it.productCategory() == productCategoryId }
        var vCpu = 0.0
        var memory = 0L
        relevantNodes.forEachGraal {
            val capacity = it.retrieveCapacity()
            vCpu += capacity.cpu
            memory += capacity.memory
        }

        return CpuAndMemory(vCpu, memory)
    }

    suspend fun retrieveUsedCapacity(productCategoryId: String): CpuAndMemory {
        val jobs = runtime.list().filter { it.productCategory() == productCategoryId }
        var vCpu = 0.0
        var memory = 0L
        for (job in jobs) {
            vCpu += job.vCpuMillis / 1000
            memory += job.memoryMegabytes * (1024 * 1024)
        }

        return CpuAndMemory(vCpu, memory)
    }

    suspend fun retrieveQueueStatus(productCategoryId: String): QueueStatus {
        val allJobs = runtime.list().filter { it.productCategory() == productCategoryId }
        val expectedJobs = allJobs
            .asSequence()
            .map { it.jobId }
            .toSet()
            .mapNotNull { k8.jobCache.findJob(it) }
            .sumOf { it.specification.replicas }

        return QueueStatus(allJobs.size, max(0, expectedJobs - allJobs.size))
    }
}
