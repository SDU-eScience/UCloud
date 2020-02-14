package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.orchestrator.api.MountMode
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.api.model.FlexVolumeSource
import io.fabric8.kubernetes.api.model.LocalObjectReference
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeMount
import io.ktor.http.HttpStatusCode

data class PreparedWorkspace(
    val user: UserContainer,
    val volumes: List<Volume>
) {
    data class UserContainer(
        val mounts: List<VolumeMount>
    )
}


class WorkspaceService(private val hostTemporaryStorage: String) {
    fun prepare(job: VerifiedJob): PreparedWorkspace {
        return when (job.mountMode ?: MountMode.COPY_FILES) {
            MountMode.COPY_FILES -> prepareCopyFiles(job)
            MountMode.COPY_ON_WRITE -> prepareCopyOnWrite(job)
        }
    }

    private fun prepareCopyFiles(job: VerifiedJob): PreparedWorkspace {
        val workspace = job.workspace ?: throw RPCException("No workspace found", HttpStatusCode.BadRequest)
        val volumes = listOf(
            volume {
                name = DATA_STORAGE
                persistentVolumeClaim = PersistentVolumeClaimVolumeSource(
                    "cephfs",
                    false
                )
            }
        )

        val mounts = listOf(
            volumeMount {
                mountPath = WORKING_DIRECTORY
                name = DATA_STORAGE
                readOnly = false
                subPath = workspace
                    .removePrefix("/")
                    .removeSuffix("/")
                    .let { "$it/output" }
            },

            volumeMount {
                mountPath = INPUT_DIRECTORY
                name = DATA_STORAGE
                readOnly = true
                subPath = workspace
                    .removePrefix("/")
                    .removeSuffix("/")
                    .let { "$it/input" }
            }
        )

        return PreparedWorkspace(
            user = PreparedWorkspace.UserContainer(mounts),
            volumes = volumes
        )
    }

    private fun prepareCopyOnWrite(job: VerifiedJob): PreparedWorkspace {
        // We are just interested in keeping the workspace ID
        val workspace = job.workspace ?: throw RPCException("No workspace found", HttpStatusCode.BadRequest)
        val workspaceId = workspace.removePrefix("/").removePrefix("workspace").removeSuffix("/")

        val cow =
            job.cow ?: throw RPCException("No CoW workspace description found", HttpStatusCode.InternalServerError)

        val volumes = mutableListOf<Volume>()
        val userMounts = mutableListOf<VolumeMount>()

        run {
            // Add snapshot volumes + mounts
            volumes += cow.snapshots.mapIndexed { idx, snapshot ->
                volume {
                    name = "$DATA_STORAGE-$idx"
                    flexVolume = FlexVolumeSource().apply {
                        driver = "ucloud/cow"
                        fsType = "ext4"
                        secretRef = LocalObjectReference().apply { name = "cow-lower" }
                        options = mapOf(
                            "workspace" to workspaceId,
                            "directoryName" to snapshot.directoryName,
                            "snapshotPath" to snapshot.snapshotPath,
                            "realPath" to snapshot.realPath,
                            "tmpStorage" to hostTemporaryStorage
                        )
                    }
                }
            }

            userMounts += cow.snapshots.mapIndexed { idx, snapshot ->
                volumeMount {
                    name = "$DATA_STORAGE-$idx"
                    mountPath = "$WORKING_DIRECTORY/${snapshot.directoryName}"
                }
            }
        }

        run {
            // Add general /work volume + mount
            volumes += volume {
                name = DATA_STORAGE
                persistentVolumeClaim = PersistentVolumeClaimVolumeSource(
                    "cephfs",
                    false
                )
            }

            userMounts += volumeMount {
                name = DATA_STORAGE
                mountPath = WORKING_DIRECTORY
                subPath = workspace.removePrefix("/").removeSuffix("/").let { "$it/work" }
            }
        }

        return PreparedWorkspace(
            PreparedWorkspace.UserContainer(userMounts),
            volumes
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
