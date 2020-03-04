package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeMount

data class PreparedWorkspace(
    val mounts: List<VolumeMount>,
    val volumes: List<Volume>
)

class WorkspaceService(private val cephConfiguration: CephConfiguration = CephConfiguration()) {
    fun prepare(job: VerifiedJob): PreparedWorkspace {
        val mounts = ArrayList<VolumeMount>()
        val volume = volume {
            name = "data"
            persistentVolumeClaim = PersistentVolumeClaimVolumeSource("cephfs", false)
        }

        job.mounts.forEach { mount ->
            mounts.add(
                volumeMount {
                    name = "data"
                    readOnly = mount.readOnly
                    mountPath = joinPath("/work", mount.destinationFileName)
                    subPath = buildString {
                        if (cephConfiguration.subfolder.isNotEmpty()) {
                            append(
                                cephConfiguration.subfolder
                                    .removePrefix("/")
                                    .removeSuffix("/")
                            )
                            append("/")
                        }
                        append(mount.sourcePath.normalize().removePrefix("/"))
                    }
                }
            )
        }

        return PreparedWorkspace(mounts, listOf(volume))
    }

    companion object : Loggable {
        override val log = logger()
    }
}
