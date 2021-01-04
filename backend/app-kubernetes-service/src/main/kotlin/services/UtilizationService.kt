package dk.sdu.cloud.app.kubernetes.services

import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.CpuAndMemory
import dk.sdu.cloud.app.orchestrator.api.JobUtilization
import dk.sdu.cloud.service.k8.*

/**
 * A service responsible for fetching utilization information
 */
class UtilizationService(
    private val k8: K8Dependencies
) {
    suspend fun allocatable(): CpuAndMemory {
        val namespace = kotlin.runCatching {
            k8.client.getResource<Namespace>(
                KubernetesResources.namespaces.withName("app-kubernetes")
            )
        }.getOrThrow()

        val computeAnnotation = namespace.metadata?.annotations?.get("scheduler.alpha.kubernetes.io/node-selector").toString()
        val nodes = if (computeAnnotation == "ucloud-compute=true") {
            kotlin.runCatching {
                k8.client.listResources<Node>(
                    KubernetesResources.node.withNamespace(NAMESPACE_ANY)
                ).items.filter { node ->
                    node.metadata?.labels?.get("ucloud-compute") == "true"
                }
            }
        } else {
            kotlin.runCatching {
                k8.client.listResources(
                    KubernetesResources.node.withNamespace(NAMESPACE_ANY)
                )
            }
        }

        val nodeAllocatableCpu = nodes.getOrThrow().sumOf { node ->
            node.status?.allocatable?.cpu?.toDouble() ?: 0.0
        }

        val nodeAllocatableMemory = nodes.getOrThrow().sumOf { node ->
            memoryStringToBytes(node.status?.allocatable?.memory)
        }

        return CpuAndMemory(nodeAllocatableCpu, nodeAllocatableMemory)
    }

    suspend fun used(): CpuAndMemory {
        val jobs = runCatching {
            k8.client.listResources<VolcanoJob>(
                KubernetesResources.volcanoJob.withNamespace("app-kubernetes")
            )
        }.getOrThrow()

        val cpuUsage = jobs.sumOf { job ->
            job.spec?.tasks?.sumOf { task ->
                task.template?.spec?.containers?.sumOf { container ->
                    cpuStringToCores(container.resources?.limits?.get("cpu")?.toString())
                } ?: 0.0
            } ?: 0.0
        }

        val memoryUsage = jobs.sumOf { job ->
            job.spec?.tasks?.sumOf { task ->
                task.template?.spec?.containers?.sumOf { container ->
                    memoryStringToBytes(container.resources?.limits?.get("memory")?.toString())
                } ?: 0
            } ?: 0
        }

        return CpuAndMemory(cpuUsage, memoryUsage)
    }

    suspend fun jobs(): JobUtilization {
        val jobs = kotlin.runCatching {
            k8.client.listResources<VolcanoJob>(
                KubernetesResources.volcanoJob.withNamespace("app-kubernetes")
            )
        }.getOrThrow()

        val running = jobs.filter { job ->
            job.status?.running == 1
        }.length

        val pending = jobs.filter { job ->
            job.status?.pending == 1
        }.length

        return JobUtilization(running, pending)
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