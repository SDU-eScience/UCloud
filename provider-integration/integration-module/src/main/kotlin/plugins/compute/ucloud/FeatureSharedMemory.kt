package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job

/**
 * A plugin which adds a /dev/shm
 *
 * The size of the shared memory device is set to be limited by the RAM allocated to the job itself. If the job has
 * no RAM reservation attached it will default to 1GB of RAM.
 */
object FeatureSharedMemory : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        val product = resources.findResources(job).product
        val sizeInGigs = product.memoryInGigs ?: 1
        builder.mountSharedMemory((sizeInGigs * 1024).toLong())
    }
}
