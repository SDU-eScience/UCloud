package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.Volume

/**
 * A plugin which mounts user-input into the containers
 */
class FileMountPlugin(
    private val cephConfiguration: CephConfiguration = CephConfiguration()
) : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        data class FileMount(val path: String, val readOnly: Boolean) {
            val fileName = path.normalize().fileName()
        }
        val fileMounts = run {
            val allMounts =
                job.mounts.map { FileMount(it.sourcePath, it.readOnly) } +
                    job.files.map { FileMount(it.sourcePath, it.readOnly) }

            allMounts.associateBy { it.path }.values
        }

        val tasks = builder.spec?.tasks ?: error("no volcano tasks")
        tasks.forEach { task ->
            val pSpec = task.template?.spec ?: error("no pod spec in task")
            pSpec.containers?.forEach { c ->
                (c.volumeMounts?.toMutableList() ?: ArrayList()).let { volumeMounts ->
                    fileMounts.forEach { mount ->
                        volumeMounts.add(
                            Pod.Container.VolumeMount(
                                name = VOL_NAME,
                                readOnly = mount.readOnly,
                                mountPath = joinPath("/work", mount.fileName),
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
                            )
                        )
                    }

                    val outputFolder = job.outputFolder
                    if (outputFolder != null) {
                        volumeMounts.add(
                            Pod.Container.VolumeMount(
                                name = VOL_NAME,
                                mountPath = "/work",
                                readOnly = false,
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
                            )
                        )
                    }

                    c.volumeMounts = volumeMounts
                }
            }

            (pSpec.volumes?.toMutableList() ?: ArrayList()).let { volumes ->
                volumes.add(
                    Volume(
                        name = VOL_NAME,
                        persistentVolumeClaim = Volume.PersistentVolumeClaimSource(CEPHFS, false)
                    )
                )
                pSpec.volumes = volumes
            }
        }
    }

    companion object {
        const val CEPHFS = "cephfs"
        const val VOL_NAME = "data"
    }
}