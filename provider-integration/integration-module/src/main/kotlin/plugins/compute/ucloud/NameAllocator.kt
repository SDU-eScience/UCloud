package dk.sdu.cloud.plugins.compute.ucloud

class NameAllocator(
    private val namespace: String,

    // HACK(Dan): Ideally the runtime controls all of this. The NameAllocator probably shouldn't exist at all.
    var runtime: ContainerRuntime? = null
) {
    fun namespace(): String {
        return namespace
    }

    fun jobIdToJobName(jobId: String): String {
        // NOTE(Dan): This needs to be a valid DNS name. The job IDs currently use UUIDs as their ID, these are not
        // guaranteed to be valid DNS entries, but only because they might start with a number. As a result, we
        // prepend our jobs with a letter, making them valid DNS names.
        return "j-${jobId}"
    }

    fun jobNameToJobId(jobName: String): String {
        return jobName.removePrefix("j-")
    }

    fun jobIdFromPodName(podName: String): String {
        return podName.substringBeforeLast('-').removeSuffix("-job").substringAfter('-')
    }

    fun rankFromPodName(podName: String): Int {
        return podName.substringAfterLast('-').toInt()
    }

    fun jobIdAndRankToPodName(jobId: String, rank: Int): String {
        if (runtime is K8PodRuntime) return "j-$jobId-$rank"
        return jobIdToJobName(jobId) + "-job-" + rank
    }

    companion object {
        const val nodeLabel = "ucloud-compute"
    }
}
