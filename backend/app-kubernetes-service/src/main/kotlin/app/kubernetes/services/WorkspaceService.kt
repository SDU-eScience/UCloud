package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.api.model.FlexVolumeSource
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeMount

data class PreparedWorkspace(
    val mounts: List<VolumeMount>,
    val volumes: List<Volume>
)

val CEPHFS = "cephfs"

class WorkspaceService(
    private val cephConfiguration: CephConfiguration = CephConfiguration(),
    private val publicIpInterface: String? = null
) {
    fun prepare(job: VerifiedJob): PreparedWorkspace {
        val mounts = ArrayList<VolumeMount>()
        val volumes = ArrayList<Volume>()
        volumes.add(volume {
            name = "data"
            persistentVolumeClaim = PersistentVolumeClaimVolumeSource(CEPHFS, false)
        })

        if (job.ipAddress != null && publicIpInterface != null) {
            val volName = "ipman"

            volumes.add(volume {
                name = volName
                flexVolume = FlexVolumeSource().apply {
                    driver = "ucloud/ipman"
                    fsType = "ext4"
                    options = mapOf(
                        "addr" to job.ipAddress,
                        "iface" to publicIpInterface
                    )
                }
            })

            mounts.add(volumeMount {
                name = volName
                readOnly = true
                mountPath = "/mnt/.ucloud_ip"
            })
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

        return PreparedWorkspace(mounts, volumes)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
