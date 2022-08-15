package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.CpuAndMemory
import dk.sdu.cloud.app.orchestrator.api.QueueStatus
import kotlinx.serialization.json.JsonPrimitive

/**
 * A service responsible for fetching utilization information
 */
class UtilizationService(
    private val k8: K8Dependencies,
) {
    suspend fun retrieveCapacity(productCategoryId: String): CpuAndMemory {
        val namespace = k8.client.getResource(
            Namespace.serializer(),
            KubernetesResources.namespaces.withName(k8.nameAllocator.namespace())
        )

        val computeAnnotation =
            (namespace.metadata?.annotations?.get("scheduler.alpha.kubernetes.io/node-selector") as? JsonPrimitive)
                ?.content

        val allNodes = k8.client.listResources(Node.serializer(), KubernetesResources.node.withNamespace(NAMESPACE_ANY))
            .items
            .filter { node ->
                computeAnnotation != "${NameAllocator.nodeLabel}=true" ||
                    (node.metadata?.labels?.get(NameAllocator.nodeLabel) as? JsonPrimitive)?.content == "true"
            }

        val nodes = allNodes.filter { node ->
            (node.metadata?.labels?.get("ucloud.dk/machine") as? JsonPrimitive)?.content == productCategoryId
        }.ifEmpty { allNodes }

        val nodeAllocatableCpu = nodes.sumOf { node ->
            node.status?.allocatable?.cpu?.toDouble() ?: 0.0
        }

        val nodeAllocatableMemory = nodes.sumOf { node ->
            memoryStringToBytes(node.status?.allocatable?.memory)
        }

        return CpuAndMemory(nodeAllocatableCpu, nodeAllocatableMemory)
    }

    suspend fun retrieveUsedCapacity(productCategoryId: String): CpuAndMemory {
        val allJobs = k8.client.listResources(
            VolcanoJob.serializer(),
            KubernetesResources.volcanoJob.withNamespace(k8.nameAllocator.namespace())
        ).filter { job ->
            job.status?.state?.phase == VolcanoJobPhase.Running
        }

        val jobs = allJobs.filter { job ->
            (job.spec?.tasks?.get(0)?.template?.spec?.nodeSelector?.get("ucloud.dk/machine") as? JsonPrimitive)?.content == productCategoryId
        }.ifEmpty { allJobs }

        val cpuUsage = jobs.sumOf { job ->
            job.spec?.tasks?.sumOf { task ->
                task.template?.spec?.containers?.sumOf { container ->
                    cpuStringToCores(container.resources?.limits?.get("cpu")?.toString()) * (job.status?.running ?: 0)
                } ?: 0.0
            } ?: 0.0
        }

        val memoryUsage = jobs.sumOf { job ->
            job.spec?.tasks?.sumOf { task ->
                task.template?.spec?.containers?.sumOf { container ->
                    memoryStringToBytes(container.resources?.limits?.get("memory")?.toString()) * (job.status?.running
                        ?: 0)
                } ?: 0
            } ?: 0
        }

        return CpuAndMemory(cpuUsage, memoryUsage)
    }

    suspend fun retrieveQueueStatus(productCategoryId: String): QueueStatus {
        val allJobs = k8.client.listResources(
            VolcanoJob.serializer(),
            KubernetesResources.volcanoJob.withNamespace(k8.nameAllocator.namespace())
        )

        val jobs = allJobs.filter { job ->
            (job.spec?.tasks?.get(0)?.template?.spec?.nodeSelector?.get("ucloud.dk/machine") as? JsonPrimitive)?.content == productCategoryId
        }.ifEmpty { allJobs }

        val runningJobs = jobs.filter { job ->
            job.status?.state?.phase == VolcanoJobPhase.Running
        }

        val pending = jobs.filter { job ->
            (job.status?.running ?: 0) < (job.spec?.minAvailable ?: 0)
        }.size

        return QueueStatus(runningJobs.size, pending)
    }

    private fun memoryStringToBytes(memory: String?): Long {
        val numbersOnly = Regex("[^0-9]")
        val ki = 1024
        val mi = 1048576
        val gi = 1073741824
        val ti = 1099511627776
        val pi = 1125899906842624

        return if (memory.isNullOrBlank()) {
            0
        } else (when {
            memory.contains("Ki") -> {
                numbersOnly.replace(memory, "").toLong() * ki
            }
            memory.contains("Mi") -> {
                numbersOnly.replace(memory, "").toLong() * mi
            }
            memory.contains("Gi") -> {
                numbersOnly.replace(memory, "").toLong() * gi
            }
            memory.contains("Ti") -> {
                numbersOnly.replace(memory, "").toLong() * ti
            }
            memory.contains("Pi") -> {
                numbersOnly.replace(memory, "").toLong() * pi
            }
            else -> {
                numbersOnly.replace(memory, "").toLong()
            }
        })
    }

    private fun cpuStringToCores(cpus: String?): Double {
        val numbersOnly = Regex("[^0-9]")

        return if (cpus.isNullOrBlank()) {
            0.toDouble()
        } else (when {
            cpus.contains("m") -> {
                numbersOnly.replace(cpus, "").toDouble() / 1000
            }
            else -> {
                numbersOnly.replace(cpus, "").toDouble()
            }
        })
    }
}
