package dk.sdu.cloud.plugins.compute.ucloud

class NameAllocator(private val namespace: String) {
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

    fun rankFromPodName(podName: String): Int {
        return podName.substringAfterLast('-').toInt()
    }

    suspend fun jobIdAndRankToPodName(jobId: String, rank: Int): String {
        return jobIdToJobName(jobId) + "-job-" + rank
    }

    companion object {
        const val nodeLabel = "ucloud-compute"
    }
}
