package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.config.ConfigSchema

data class NodeConfiguration(
    val systemReservedCpuMillis: Int,
    val systemReservedMemMegabytes: Int,
    val types: Map<String, NodeType>
)

data class NodeType(
    val cpuMillis: Int,
    val memMegabytes: Int,
    val gpus: Int,
)

/**
 * A plugin which initializes the Volcano task
 *
 * This will create a single task that will start the user's container in the appropriate amount of copies with the
 * correct resource (CPU/RAM/GPU) allocation.
 *
 * Most other plugins depend on this plugin having run.
 */
class FeatureTask(
    private val toleration: ConfigSchema.Plugins.Jobs.UCloud.TolerationKeyAndValue?,
    private val useSmallReservation: Boolean,
    private val useMachineSelector: Boolean,
    private val nodes: NodeConfiguration?,
) : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        val jobResources = resources.findResources(job)
        val app = jobResources.application.invocation
        val tool = app.tool.tool!!.description

        @Suppress("DEPRECATION")
        builder.image(tool.container!!)

        if (builder is PodBasedContainer) {
            val pSpec = builder.pod.spec!!

            if (toleration != null) {
                pSpec.tolerations = listOf(
                    Pod.Toleration("NoSchedule", toleration.key, "Equal", null, toleration.value)
                )
            }
        }

        if (useMachineSelector) builder.productCategoryRequired = job.specification.product.category

        val reservedCpu = nodes?.systemReservedCpuMillis ?: 0
        val reservedMem = nodes?.systemReservedMemMegabytes ?: 0
        val totalCpu = nodes?.types?.get(jobResources.product.category.name)?.cpuMillis
        val totalMem = nodes?.types?.get(jobResources.product.category.name)?.memMegabytes
        val reservation = jobResources.product

        if(job.owner.createdBy.startsWith("simulated-user-")) {
            builder.vCpuMillis = 100
            builder.memoryMegabytes = 100
        } else if (useSmallReservation) {
            builder.vCpuMillis = reservation.cpu!! * 100
            builder.memoryMegabytes = 128
        } else {
            val requestedCpu = reservation.cpu!! * 1000
            builder.vCpuMillis = if (totalCpu != null) {
                (requestedCpu - (reservedCpu * (requestedCpu / totalCpu.toDouble()))).toInt()
            } else {
                requestedCpu
            }

            val requestedMem = reservation.memoryInGigs!! * 1000
            builder.memoryMegabytes = if (totalMem != null) {
                (requestedMem - (reservedMem * (requestedMem / totalMem.toDouble()))).toInt()
            } else {
                requestedMem
            }
        }

        val gpus = reservation.gpu
        if (gpus != null) {
            builder.gpus = gpus
        }
    }
}

const val DEFAULT_QUEUE = "default"
const val USER_JOB_CONTAINER = "user-job"
