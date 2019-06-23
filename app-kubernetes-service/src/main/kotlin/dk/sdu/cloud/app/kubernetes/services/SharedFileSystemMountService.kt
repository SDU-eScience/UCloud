package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.fs.kubernetes.api.ROOT_DIRECTORY
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeMount
import io.ktor.http.HttpStatusCode

class SharedFileSystemMountService {
    fun createVolumesAndMounts(job: VerifiedJob): Pair<List<Volume>, List<VolumeMount>> {
        val volumes = ArrayList<Volume>()
        val mounts = ArrayList<VolumeMount>()

        job.sharedFileSystemMounts.forEachIndexed { idx, mount ->
            val id = mount.sharedFileSystem.id

            // Most of the verification is done by the app-service we just do a quick sanity check here.
            if (id.contains(".") || id.contains("/")) {
                throw RPCException(
                    "Rejecting shared file system due to its suspicious ID: $id",
                    HttpStatusCode.InternalServerError
                )
            }

            mounts.add(
                VolumeMount(
                    mount.mountedAt,
                    null,
                    DATA_STORAGE,
                    false,
                    "$ROOT_DIRECTORY/$id"
                )
            )
        }

        return Pair(volumes, mounts)
    }
}
