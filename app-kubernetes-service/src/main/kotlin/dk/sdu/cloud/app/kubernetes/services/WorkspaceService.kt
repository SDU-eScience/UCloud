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


class WorkspaceService {
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
        val workspace = job.workspace?.removePrefix("/")?.removePrefix("workspace")?.removeSuffix("/")
            ?: throw RPCException("No workspace found", HttpStatusCode.BadRequest)

        val volumes = listOf(
            volume {
                name = DATA_STORAGE
                flexVolume = FlexVolumeSource().apply {
                    driver = "ucloud/cow"
                    fsType = "ext4"
                    secretRef = LocalObjectReference().apply { name = "cow-lower" }
                    options = mapOf(
                        "workspace" to workspace
                    )
                }
            }
        )

        val userMounts = listOf(
            volumeMount {
                name = DATA_STORAGE
                mountPath = WORKING_DIRECTORY
            }
       )

        return PreparedWorkspace(
            PreparedWorkspace.UserContainer(userMounts),
            volumes
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
