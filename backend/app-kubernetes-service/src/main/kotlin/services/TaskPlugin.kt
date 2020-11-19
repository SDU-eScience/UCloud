package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.TolerationKeyAndValue
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.service.k8.ObjectMeta
import dk.sdu.cloud.service.k8.Pod

/**
 * A plugin which initializes the Volcano task
 *
 * This will create a single task that will start the user's container in the appropriate amount of copies with the
 * correct resource (CPU/RAM/GPU) allocation.
 *
 * Most other plugins depend on this plugin having run.
 */
class TaskPlugin(private val toleration: TolerationKeyAndValue?) : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val jobResources = resources.findResources(job)
        val app = jobResources.application.invocation
        val tool = app.tool.tool!!.description

        val vSpec = builder.spec ?: error("no volcano job spec")
        vSpec.minAvailable = job.parameters.replicas
        vSpec.queue = DEFAULT_QUEUE
        vSpec.policies = emptyList()
        vSpec.plugins = mapOf(
            // I don't believe we will need this. TODO(Dan): Ask Emiliano if he is interested
            //"ssh" to ArrayList(),
            "env" to ArrayList(),
            "svc" to ArrayList(),
        )
        vSpec.maxRetry = 0
        (vSpec.tasks?.toMutableList() ?: ArrayList()).let { tasks ->
            tasks.add(VolcanoJob.TaskSpec().apply {
                name = "job"
                replicas = job.parameters.replicas
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

                    container.name = "user-job"
                    container.image = tool.container
                    container.imagePullPolicy = "IfNotPresent"
                    container.resources = run {
                        val resources = HashMap<String, String>()
                        val reservation = jobResources.product

                        if (reservation.cpu != null) {
                            resources += "cpu" to "${reservation.cpu!! * 1000}m"
                        }

                        if (reservation.memoryInGigs != null) {
                            resources += "memory" to "${reservation.memoryInGigs!!}Gi"
                        }

                        if (reservation.gpu != null) {
                            resources += "nvidia.com/gpu" to "${reservation.gpu!!}"
                        }

                        if (resources.isNotEmpty()) {
                            Pod.Container.ResourceRequirements(
                                limits = resources,
                                requests = resources,
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