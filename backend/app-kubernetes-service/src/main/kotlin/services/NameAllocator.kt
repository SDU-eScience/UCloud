package dk.sdu.cloud.app.kubernetes.services

class NameAllocator {
    suspend fun jobIdToNamespace(jobId: String): String {
        return "app-kubernetes"
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
}