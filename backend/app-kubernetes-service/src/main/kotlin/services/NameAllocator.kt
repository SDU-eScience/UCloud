package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.*

// NOTE(Dan): Fair share is currently disabled due to various security concerns (See #1831)
internal const val fairShareEnabled = false

class NameAllocator(
    private val jobCache: VerifiedJobCache,
    private val uidCache: UserUidCache,
) {
    suspend fun jobIdToNamespace(jobId: String): String {
        if (!fairShareEnabled) return "app-kubernetes"

        // NOTE(Dan): Volcano uses namespaces to control fair-share (used to differentiate between different users). We
        // will use a namespace for every type of application owner. That is, if the owner is a user we will create a
        // namespace for them. If the application has a project attached to it we will use a namespace dedicated to
        // the project. Namespace creation is done, as needed, by the FairSharePlugin.
        val cachedJob = jobCache.findJob(jobId) ?: throw RPCException(
            "Unknown job with ID: $jobId",
            HttpStatusCode.InternalServerError
        )

        return if (cachedJob.project != null) {
            "app-p-${cachedJob.project}"
        } else {
            // NOTE(Dan): We use the UID of the user instead of the username since this is the easiest way to
            // create a safe namespace name.
            "app-u-${uidCache.cache.get(cachedJob.owner)}"
        }
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
}