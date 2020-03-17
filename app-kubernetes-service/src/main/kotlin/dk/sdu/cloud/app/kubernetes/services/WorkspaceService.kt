package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.file.api.fileName
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

        data class FileMount(val path: String, val readOnly: Boolean) {
            val fileName = path.normalize().fileName()
        }
        val fileMounts = run {
            val allMounts =
                job.mounts.map { FileMount(it.sourcePath, it.readOnly) } +
                        job.files.map { FileMount(it.sourcePath, it.readOnly) }

            allMounts.associateBy { it.path }.values
        }

        fileMounts.forEach { mount ->
            mounts.add(
                volumeMount {
                    name = "data"
                    readOnly = mount.readOnly
                    mountPath = joinPath("/work", mount.fileName)
                    subPath = buildString {
                        if (cephConfiguration.subfolder.isNotEmpty()) {
                            append(
                                cephConfiguration.subfolder
                                    .removePrefix("/")
                                    .removeSuffix("/")
                            )
                            append("/")
                        }
                        append(mount.path.normalize().removePrefix("/"))
                    }
                }
            )
        }

        val outputFolder = job.outputFolder
        if (outputFolder != null) {
            mounts.add(
                volumeMount {
                    name = "data"
                    mountPath = "/work"
                    readOnly = false
                    subPath = buildString {
                        if (cephConfiguration.subfolder.isNotEmpty()) {
                            append(
                                cephConfiguration.subfolder
                                    .removePrefix("/")
                                    .removeSuffix("/")
                            )
                            append("/")
                        }
                        append(outputFolder.normalize().removePrefix("/"))
                    }
                }
            )
        } else {
            log.warn("No output folder found!")
        }

        return PreparedWorkspace(mounts, listOf(volume))
    }

    companion object : Loggable {
        override val log = logger()
    }
}
