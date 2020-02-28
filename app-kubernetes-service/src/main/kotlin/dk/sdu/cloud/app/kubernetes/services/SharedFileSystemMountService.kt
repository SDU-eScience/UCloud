package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.fs.kubernetes.api.ROOT_DIRECTORY
import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeMount
import io.ktor.http.HttpStatusCode

class SharedFileSystemMountService(private val cephConfiguration: CephConfiguration = CephConfiguration()) {
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

            val directory = if (cephConfiguration.subfolder.isNotBlank()) {
                cephConfiguration.subfolder + "/$ROOT_DIRECTORY/$id"
            } else {
                "$ROOT_DIRECTORY/$id"
            }

            mounts.add(
                VolumeMount(
                    mount.mountedAt,
                    null,
                    DATA_STORAGE,
                    false,
                    directory,
                    null
                )
            )
        }

        return Pair(volumes, mounts)
    }
}
