package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.CephConfiguration
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.files
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.PathConverter.Companion.PERSONAL_REPOSITORY
import dk.sdu.cloud.prettyMapper
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.Volume
import kotlinx.serialization.encodeToString

/**
 * A plugin which mounts user-input into the containers
 */
class FileMountPlugin(
    private val fs: NativeFS,
    private val memberFiles: MemberFiles,
    private val pathConverter: PathConverter,
    private val limitChecker: LimitChecker,
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

        try {
            fs.createDirectories(file)
        } catch (ex: FSException.NotFound) {
            log.warn("Unable to create directory, needed for file mounts: $file")
            throw ex
        }

        val jobParameterJson = job.status.jobParametersJson
        if (jobParameterJson != null) {
            val jobParamsFile = InternalFile(
                joinPath(
                    file.path.removeSuffix("/"),
                    "JobParameters.json"
                ).removeSuffix("/")
            )
            try {
                fs.openForWriting(jobParamsFile, WriteConflictPolicy.RENAME).second.bufferedWriter().use {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    it.write(prettyMapper.encodeToString(jobParameterJson))
                }
            } catch (ex: Throwable) {
                log.warn("Unable to create JobParameters.json for job: ${job.id} ${jobParamsFile}. ${ex.stackTraceToString()}")
            }
        }

        return file
    }

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        data class FileMount(val path: String, val readOnly: Boolean) {
            val fileName = path.normalize().fileName()
        }

        val fileCollections = job.files.map {
            it.path.normalize().components().getOrNull(0) ?: error("Unexpected path: $it")
        }

        for (coll in fileCollections) {
            limitChecker.checkLimit(coll)
        }

        val fileMounts = run {
            val allMounts = job.files.map {
                val internalFile = pathConverter.ucloudToRelative(UCloudFile.create(it.path))
                FileMount(internalFile.path, it.readOnly)
            }

            allMounts.associateBy { it.path }.values
        }

        val jobFolder = findJobFolder(job)
        val relativeJobFolder = pathConverter.internalToRelative(jobFolder)
        val ucloudJobFolder = pathConverter.internalToUCloud(jobFolder)

        val jobFolderCollection = ucloudJobFolder.path.components().getOrNull(0)
            ?: error("Unexpected job folder: $ucloudJobFolder")
        limitChecker.checkLimit(jobFolderCollection)

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

    override suspend fun JobManagement.onJobComplete(jobId: String, jobFromServer: VolcanoJob) {
        val workMount = findWorkMount(jobFromServer)
        val volumeMounts =
            jobFromServer.spec?.tasks?.getOrNull(0)?.template?.spec?.containers?.getOrNull(0)?.volumeMounts

        if (workMount != null && volumeMounts != null) {
            for (mount in volumeMounts) {
                val mountPath = (mount.mountPath)?.removeSuffix("/") ?: continue
                if (mountPath == "/work" || !mountPath.startsWith("/work/") || mountPath.count { it == '/' } != 2) {
                    continue
                }

                val mountedDirectoryName = mountPath.removePrefix("/work/")
                val mountedDirectory = pathConverter.relativeToInternal(
                    RelativeInternalFile(joinPath(workMount.path, mountedDirectoryName).removeSuffix("/"))
                )

                try {
                    fs.delete(mountedDirectory, allowRecursion = false)
                } catch (ex: Throwable) {
                    log.info("Caught exception while cleaning up empty mount directories for:" +
                        "\n\tjob = $jobId" +
                        "\n\tmountedDirectory=$mountedDirectory" +
                        "\n\tvolcanoJob=$jobFromServer"
                    )
                    log.info(ex.stackTraceToString())
                }
            }
        }
    }

    fun findWorkMount(jobFromServer: VolcanoJob): RelativeInternalFile? {
        return jobFromServer.spec?.tasks?.getOrNull(0)?.template?.spec?.containers?.getOrNull(0)?.volumeMounts
            ?.find { it.name == VOL_NAME }
            ?.subPath
            ?.let { path ->
                if (cephConfiguration.subfolder.isNotEmpty()) {
                    path.removePrefix(cephConfiguration.subfolder.removePrefix("/")).removePrefix("/")
                } else {
                    path
                }
            }
            ?.let { path -> RelativeInternalFile("/${path}") }
    }

    companion object : Loggable {
        const val CEPHFS = "cephfs"
        const val VOL_NAME = "data"
        const val JOBS_FOLDER = "Jobs"

        override val log = logger()
    }
}
