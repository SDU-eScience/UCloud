package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.NodeConfiguration
import dk.sdu.cloud.app.kubernetes.TolerationKeyAndValue
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.service.k8.ObjectMeta
import dk.sdu.cloud.service.k8.Pod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A plugin which initializes the Volcano task
 *
 * This will create a single task that will start the user's container in the appropriate amount of copies with the
 * correct resource (CPU/RAM/GPU) allocation.
 *
 * Most other plugins depend on this plugin having run.
 */
class TaskPlugin(
    private val toleration: TolerationKeyAndValue?,
    private val useSmallReservation: Boolean,
    private val useMachineSelector: Boolean,
    private val nodes: NodeConfiguration?,
) : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val jobResources = resources.findResources(job)
        val app = jobResources.application.invocation
        val tool = app.tool.tool!!.description

        val vSpec = builder.spec ?: error("no volcano job spec")
        vSpec.minAvailable = job.specification.replicas
        vSpec.queue = DEFAULT_QUEUE
        vSpec.policies = emptyList()
        vSpec.plugins = JsonObject(mapOf(
            "env" to JsonArray(emptyList()),
            "svc" to JsonArray(emptyList()),
        ))
        vSpec.maxRetry = 0
        (vSpec.tasks?.toMutableList() ?: ArrayList()).let { tasks ->
            tasks.add(VolcanoJob.TaskSpec().apply {
                name = "job"
                replicas = job.specification.replicas
                policies = emptyList()
                template = Pod.SpecTemplate().apply {
                    metadata = ObjectMeta(name = "job-${job.id}")
                    val pSpec = Pod.Spec()
                    spec = pSpec

                    val container = Pod.Container()
                    pSpec.containers = listOf(container)
                    if (toleration != null) {
                        pSpec.tolerations = listOf(
                            Pod.Toleration("NoSchedule", toleration.key, "Equal", null, toleration.value)
                        )
                    }

                    if (useMachineSelector) {
                        pSpec.nodeSelector = JsonObject(mapOf(
                            "ucloud.dk/machine" to JsonPrimitive(job.specification.product.category)
                        ))
                    }

                    container.name = "user-job"
                    container.image = tool.container
                    container.imagePullPolicy = "IfNotPresent"
                    container.resources = run {
                        val reservedCpu = nodes?.systemReservedCpuMillis ?: 0
                        val reservedMem = nodes?.systemReservedMemMegabytes ?: 0
                        val totalCpu = nodes?.types?.get(jobResources.product.category.id)?.cpuMillis
                        val totalMem = nodes?.types?.get(jobResources.product.category.id)?.memMegabytes

                        val resources = HashMap<String, JsonElement>()
                        val reservation = jobResources.product

                        if (reservation.cpu != null) {
                            resources += if (useSmallReservation) {
                                "cpu" to JsonPrimitive("${reservation.cpu!! * 100}m")
                            } else {
                                val requestedCpu = reservation.cpu!! * 1000
                                val actualCpu = if (totalCpu != null) {
                                    (requestedCpu - (reservedCpu * (requestedCpu / totalCpu.toDouble()))).toInt()
                                } else {
                                    requestedCpu
                                }
                                "cpu" to JsonPrimitive("${actualCpu}m")
                            }
                        }

                        if (reservation.memoryInGigs != null) {
                            resources += if (useSmallReservation) {
                                "memory" to JsonPrimitive("${reservation.memoryInGigs!!}Mi")
                            } else {
                                val requestedMem = reservation.memoryInGigs!! * 1024
                                val actualMem = if (totalMem != null) {
                                    (requestedMem - (reservedMem * (requestedMem / totalMem.toDouble()))).toInt()
                                } else {
                                    requestedMem
                                }
                                "memory" to JsonPrimitive("${actualMem}Mi")
                            }
                        }

                        if (reservation.gpu != null) {
                            resources += "nvidia.com/gpu" to JsonPrimitive("${reservation.gpu!!}")
                        }

                        if (resources.isNotEmpty()) {
                            Pod.Container.ResourceRequirements(
                                limits = JsonObject(resources),
                                requests = JsonObject(resources),
                            )
                        } else {
                            null
                        }
                    }
                }
            })

            vSpec.tasks = tasks
        }
    }
}

const val DEFAULT_QUEUE = "default"
const val USER_JOB_CONTAINER = "user-job"