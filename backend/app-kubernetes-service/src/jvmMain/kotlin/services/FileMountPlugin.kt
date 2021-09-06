package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.files
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.file.orchestrator.api.normalize
import dk.sdu.cloud.file.ucloud.services.InternalFile
import dk.sdu.cloud.file.ucloud.services.MemberFiles
import dk.sdu.cloud.file.ucloud.services.NativeFS
import dk.sdu.cloud.file.ucloud.services.PathConverter
import dk.sdu.cloud.file.ucloud.services.PathConverter.Companion.PERSONAL_REPOSITORY
import dk.sdu.cloud.file.ucloud.services.RelativeInternalFile
import dk.sdu.cloud.file.ucloud.services.UCloudFile
import dk.sdu.cloud.file.ucloud.services.normalize
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.Volume
import io.ktor.http.*

/**
 * A plugin which mounts user-input into the containers
 */
class FileMountPlugin(
    private val fs: NativeFS,
    private val memberFiles: MemberFiles,
    private val pathConverter: PathConverter,
    private val cephConfiguration: CephConfiguration = CephConfiguration(),
) : JobManagementPlugin {
    private suspend fun JobManagement.findJobFolder(job: Job): InternalFile {
        val username = job.owner.createdBy
        val project = job.owner.project

        val jobResources = resources.findResources(job)
        memberFiles.initializeMemberFiles(username, project)

        val file = if (project != null) {
            pathConverter.relativeToInternal(
                RelativeInternalFile(
                    joinPath(
                        PathConverter.PROJECT_DIRECTORY,
                        project,
                        PERSONAL_REPOSITORY,
                        username,
                        JOBS_FOLDER,
                        jobResources.application.metadata.title,
                        if (job.specification.name != null) "${job.specification.name} (${job.id})" else job.id
                    ).removeSuffix("/")
                )
            )
        } else {
            pathConverter.relativeToInternal(
                RelativeInternalFile(
                    joinPath(
                        PathConverter.HOME_DIRECTORY,
                        username,
                        JOBS_FOLDER,
                        jobResources.application.metadata.title,
                        if (job.specification.name != null) "${job.specification.name} (${job.id})" else job.id
                    )
                )
            )
        }

        fs.createDirectories(file)
        return file
    }

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        data class FileMount(val path: String, val readOnly: Boolean) {
            val fileName = path.normalize().fileName()
        }

        val fileMounts = run {
            val allMounts = job.files.map {
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(it.path))
                FileMount(internalFile.path, it.readOnly)
            }

            allMounts.associateBy { it.path }.values
        }

        val jobFolder = findJobFolder(job)
        val relativeJobFolder = pathConverter.internalToRelative(jobFolder)
        val ucloudJobFolder = pathConverter.internalToUCloud(jobFolder)

        JobsControl.update.call(
            bulkRequestOf(
                ResourceUpdateAndId(
                    job.id,
                    JobUpdate(
                        outputFolder = ucloudJobFolder.path
                    )
                )
            ),
            k8.serviceClient
        ).orRethrowAs { throw RPCException("Internal error - Could not add output folder", HttpStatusCode.BadGateway) }

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
                                append(relativeJobFolder.normalize().path.removePrefix("/"))
                            }
                        )
                    )

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
        const val JOBS_FOLDER = "Jobs"
    }
}
